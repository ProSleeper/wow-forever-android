package com.winlator.alsaserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Hands the game's sound to MotionTap (a separate helper app on the same phone) before Android's
 * volume is applied, so it can listen for the fishing bobber's splash even with the phone muted.
 *
 * The game sound is copied in {@link ALSAClient#writeDataToTrack} - the PCM the game renders, before
 * {@code AudioTrack.setVolume} and before the media volume - mixed down to mono 16-bit and served on
 * {@code 127.0.0.1:}{@link #PORT}. Nothing is copied while no client is connected.
 *
 * Wire format, all big-endian: repeated packets of
 * {@code int MAGIC ('MTA1'), int sampleRate, int frames, short[frames] mono samples}.
 */
public final class GameAudioTap {
    public static final int PORT = 47621;
    public static final int MAGIC = 0x4D544131; // "MTA1"
    private static final int QUEUE = 64;

    private static final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private static final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE);
    private static volatile boolean started = false;
    private static volatile ServerSocket server;

    private GameAudioTap() {}

    /** Starts listening once per process; later calls do nothing. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        Thread accept = new Thread(GameAudioTap::acceptLoop, "game-audio-tap-accept");
        accept.setDaemon(true);
        accept.start();
        Thread send = new Thread(GameAudioTap::sendLoop, "game-audio-tap-send");
        send.setDaemon(true);
        send.start();
    }

    public static boolean hasClients() {
        return !clients.isEmpty();
    }

    private static void acceptLoop() {
        while (true) {
            try (ServerSocket s = new ServerSocket(PORT, 2, InetAddress.getByName("127.0.0.1"))) {
                server = s;
                Timber.tag("GameAudioTap").i("Listening on 127.0.0.1:%d", PORT);
                while (true) {
                    Socket c = s.accept();
                    c.setTcpNoDelay(true);
                    clients.add(c);
                    Timber.tag("GameAudioTap").i("Client connected (%d)", clients.size());
                }
            } catch (IOException e) {
                Timber.tag("GameAudioTap").w(e, "Audio tap server stopped, retrying");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private static void sendLoop() {
        while (true) {
            byte[] packet;
            try {
                packet = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (packet == null) continue;
            for (Socket c : clients) {
                try {
                    OutputStream out = c.getOutputStream();
                    out.write(packet);
                    out.flush();
                } catch (IOException e) {
                    clients.remove(c);
                    try {
                        c.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Copies one buffer of game PCM (bytes {@code 0..limit} of {@code data}, interleaved frames). Never blocks the
     * audio thread: when the sender falls behind, the packet is dropped.
     */
    public static void offer(ByteBuffer data, ALSAClient.DataType type, int channels, int sampleRate) {
        if (clients.isEmpty() || sampleRate <= 0 || channels <= 0) return;
        byte[] packet = encode(data, type, channels, sampleRate);
        if (packet != null) queue.offer(packet);
    }

    /** Mixes {@code data} down to a mono 16-bit packet. Reads by absolute index, so the buffer's position is untouched. */
    static byte[] encode(ByteBuffer data, ALSAClient.DataType type, int channels, int sampleRate) {
        int frameBytes = type.byteCount * channels;
        int frames = data.limit() / frameBytes;
        if (frames <= 0) return null;
        ByteBuffer src = data.duplicate();
        src.order(type == ALSAClient.DataType.S16BE || type == ALSAClient.DataType.FLOATBE ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(12 + frames * 2).order(ByteOrder.BIG_ENDIAN);
        out.putInt(MAGIC).putInt(sampleRate).putInt(frames);
        for (int f = 0; f < frames; f++) {
            float sum = 0f;
            int base = f * frameBytes;
            for (int ch = 0; ch < channels; ch++) {
                int i = base + ch * type.byteCount;
                switch (type) {
                    case U8:
                        sum += ((src.get(i) & 0xFF) - 128) / 128f;
                        break;
                    case S16LE:
                    case S16BE:
                        sum += src.getShort(i) / 32768f;
                        break;
                    default:
                        sum += src.getFloat(i);
                        break;
                }
            }
            float v = sum / channels;
            if (v > 1f) v = 1f;
            else if (v < -1f) v = -1f;
            out.putShort((short) Math.round(v * 32767f));
        }
        return out.array();
    }
}
