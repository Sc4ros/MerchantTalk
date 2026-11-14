package fr.merchanttalk.web;

/** Lecture minimale de l'en-tête d'un fichier Ogg Vorbis (canaux, fréquence, durée). */
public record OggInfo(int channels, int sampleRate, double duration) {

    /** @return les infos, ou null si ce n'est pas un Ogg Vorbis valide. */
    public static OggInfo read(byte[] data) {
        if (data.length < 64 || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') return null;
        int idx = indexOf(data, new byte[]{0x01, 'v', 'o', 'r', 'b', 'i', 's'}, 0, Math.min(data.length, 512));
        if (idx < 0 || idx + 16 > data.length) return null;
        int channels = data[idx + 11] & 0xFF;
        int sampleRate = (data[idx + 12] & 0xFF) | (data[idx + 13] & 0xFF) << 8
                | (data[idx + 14] & 0xFF) << 16 | (data[idx + 15] & 0xFF) << 24;
        if (channels <= 0 || sampleRate <= 0) return null;
        // La position (granule) de la dernière page = nombre total d'échantillons.
        long granule = -1;
        for (int i = data.length - 14; i >= 0; i--) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                granule = 0;
                for (int b = 7; b >= 0; b--) granule = (granule << 8) | (data[i + 6 + b] & 0xFF);
                break;
            }
        }
        double duration = granule > 0 ? (double) granule / sampleRate : 0;
        return new OggInfo(channels, sampleRate, duration);
    }

    private static int indexOf(byte[] data, byte[] needle, int from, int to) {
        outer:
        for (int i = from; i <= to - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
