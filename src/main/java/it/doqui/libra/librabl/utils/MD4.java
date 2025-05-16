package it.doqui.libra.librabl.utils;

import java.security.MessageDigest;
import java.util.Arrays;

public class MD4 extends MessageDigest {

    /**
     * The size in bytes of the input block to the tranformation algorithm.
     */
    private static final int BLOCK_LENGTH = 64;

    /**
     * 4 32-bit words (interim result)
     */
    private final int[] context = new int[4];

    /**
     * Number of bytes processed so far mod. 2 power of 64.
     */
    private long count;

    /**
     * 512 bits input buffer = 16 x 32-bit words holds until reaches 512 bits.
     */
    private final byte[] buffer = new byte[BLOCK_LENGTH];

    /**
     * 512 bits work buffer = 16 x 32-bit words
     */
    private final int[] x = new int[16];

    public MD4() {
        super("MD4");
        engineReset();
    }

    /**
     * Resets this object disregarding any temporary data present at the
     * time of the invocation of this call.
     */
    public void engineReset () {
        // initial values of MD4 i.e. A, B, C, D
        // as per rfc-1320; they are low-order byte first
        context[0] = 0x67452301;
        context[1] = 0xEFCDAB89;
        context[2] = 0x98BADCFE;
        context[3] = 0x10325476;
        count = 0L;
        Arrays.fill(buffer, (byte) 0);
    }

    /**
     * Continues an MD4 message digest using the input byte.
     */
    public void engineUpdate (byte b) {
        // compute number of bytes still unhashed; ie. present in buffer
        int i = (int)(count % BLOCK_LENGTH);
        count++;                                        // update number of bytes
        buffer[i] = b;
        if (i == BLOCK_LENGTH - 1)
            transform(buffer, 0);
    }

    /**
     * MD4 block update operation.
     * <p>
     * Continues an MD4 message digest operation, by filling the buffer,
     * transform(ing) data in 512-bit message block(s), updating the variables
     * context and count, and leaving (buffering) the remaining bytes in buffer
     * for the next update or finish.
     *
     * @param    input    input block
     * @param    offset    start of meaningful bytes in input
     * @param    len        count of bytes in input block to consider
     */
    public void engineUpdate (byte[] input, int offset, int len) {
        // make sure we don't exceed input's allocated size/length
        if (offset < 0 || len < 0 || (long)offset + len > input.length)
            throw new ArrayIndexOutOfBoundsException();

        // compute number of bytes still unhashed; ie. present in buffer
        int bufferNdx = (int)(count % BLOCK_LENGTH);
        count += len;                                        // update number of bytes
        int partLen = BLOCK_LENGTH - bufferNdx;
        int i = 0;
        if (len >= partLen) {
            System.arraycopy(input, offset, buffer, bufferNdx, partLen);


            transform(buffer, 0);

            for (i = partLen; i + BLOCK_LENGTH - 1 < len; i+= BLOCK_LENGTH)
                transform(input, offset + i);
            bufferNdx = 0;
        }
        // buffer remaining input
        if (i < len)
            System.arraycopy(input, offset + i, buffer, bufferNdx, len - i);
    }

    /**
     * Completes the hash computation by performing final operations such
     * as padding. At the return of this engineDigest, the MD engine is
     * reset.
     *
     * @return the array of bytes for the resulting hash value.
     */
    public byte[] engineDigest () {
        // pad output to 56 mod 64; as RFC1320 puts it: congruent to 448 mod 512
        int bufferNdx = (int)(count % BLOCK_LENGTH);
        int padLen = (bufferNdx < 56) ? (56 - bufferNdx) : (120 - bufferNdx);

        // padding is alwas binary 1 followed by binary 0s
        byte[] tail = new byte[padLen + 8];
        tail[0] = (byte)0x80;

        // append length before final transform:
        // save number of bits, casting the long to an array of 8 bytes
        // save low-order byte first.
        for (int i = 0; i < 8; i++)
            tail[padLen + i] = (byte)((count * 8) >>> (8 * i));

        engineUpdate(tail, 0, tail.length);

        byte[] result = new byte[16];
        // cast this MD4's context (array of 4 ints) into an array of 16 bytes.
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                result[i * 4 + j] = (byte)(context[i] >>> (8 * j));

        // reset the engine
        engineReset();
        return result;
    }

    /**
     *    MD4 basic transformation.
     *    <p>
     *    Transforms context based on 512 bits from input block starting
     *    from the offset'th byte.
     *
     *    @param    block    input sub-array.
     *    @param    offset    starting position of sub-array.
     */
    private void transform (byte[] block, int offset) {

        // encodes 64 bytes from input block into an array of 16 32-bit
        // entities. Use A as a temp var.
        for (int i = 0; i < 16; i++)
            x[i] = (block[offset++] & 0xFF)       |
                (block[offset++] & 0xFF) <<  8 |
                (block[offset++] & 0xFF) << 16 |
                (block[offset++] & 0xFF) << 24;


        int a = context[0];
        int b = context[1];
        int c = context[2];
        int d = context[3];

        a = ff(a, b, c, d, x[ 0],  3);
        d = ff(d, a, b, c, x[ 1],  7);
        c = ff(c, d, a, b, x[ 2], 11);
        b = ff(b, c, d, a, x[ 3], 19);
        a = ff(a, b, c, d, x[ 4],  3);
        d = ff(d, a, b, c, x[ 5],  7);
        c = ff(c, d, a, b, x[ 6], 11);
        b = ff(b, c, d, a, x[ 7], 19);
        a = ff(a, b, c, d, x[ 8],  3);
        d = ff(d, a, b, c, x[ 9],  7);
        c = ff(c, d, a, b, x[10], 11);
        b = ff(b, c, d, a, x[11], 19);
        a = ff(a, b, c, d, x[12],  3);
        d = ff(d, a, b, c, x[13],  7);
        c = ff(c, d, a, b, x[14], 11);
        b = ff(b, c, d, a, x[15], 19);

        a = gg(a, b, c, d, x[ 0],  3);
        d = gg(d, a, b, c, x[ 4],  5);
        c = gg(c, d, a, b, x[ 8],  9);
        b = gg(b, c, d, a, x[12], 13);
        a = gg(a, b, c, d, x[ 1],  3);
        d = gg(d, a, b, c, x[ 5],  5);
        c = gg(c, d, a, b, x[ 9],  9);
        b = gg(b, c, d, a, x[13], 13);
        a = gg(a, b, c, d, x[ 2],  3);
        d = gg(d, a, b, c, x[ 6],  5);
        c = gg(c, d, a, b, x[10],  9);
        b = gg(b, c, d, a, x[14], 13);
        a = gg(a, b, c, d, x[ 3],  3);
        d = gg(d, a, b, c, x[ 7],  5);
        c = gg(c, d, a, b, x[11],  9);
        b = gg(b, c, d, a, x[15], 13);

        a = hh(a, b, c, d, x[ 0],  3);
        d = hh(d, a, b, c, x[ 8],  9);
        c = hh(c, d, a, b, x[ 4], 11);
        b = hh(b, c, d, a, x[12], 15);
        a = hh(a, b, c, d, x[ 2],  3);
        d = hh(d, a, b, c, x[10],  9);
        c = hh(c, d, a, b, x[ 6], 11);
        b = hh(b, c, d, a, x[14], 15);
        a = hh(a, b, c, d, x[ 1],  3);
        d = hh(d, a, b, c, x[ 9],  9);
        c = hh(c, d, a, b, x[ 5], 11);
        b = hh(b, c, d, a, x[13], 15);
        a = hh(a, b, c, d, x[ 3],  3);
        d = hh(d, a, b, c, x[11],  9);
        c = hh(c, d, a, b, x[ 7], 11);
        b = hh(b, c, d, a, x[15], 15);

        context[0] += a;
        context[1] += b;
        context[2] += c;
        context[3] += d;
    }

    // The basic MD4 atomic functions.

    private int ff(int a, int b, int c, int d, int x, int s) {
        int t = a + ((b & c) | (~b & d)) + x;
        return t << s | t >>> (32 - s);
    }
    private int gg(int a, int b, int c, int d, int x, int s) {
        int t = a + ((b & (c | d)) | (c & d)) + x + 0x5A827999;
        return t << s | t >>> (32 - s);
    }
    private int hh(int a, int b, int c, int d, int x, int s) {
        int t = a + (b ^ c ^ d) + x + 0x6ED9EBA1;
        return t << s | t >>> (32 - s);
    }
}
