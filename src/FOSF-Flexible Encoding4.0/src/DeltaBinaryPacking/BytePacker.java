package DeltaBinaryPacking;

/*
 * adapt from  parquet
 *
 */
/**
 * Packs and unpacks into bytes
 *
 * packing unpacking treats:
 *  - n values at a time (with n % 8 == 0)
 *  - bitWidth * (n/8) bytes at a time.
 *
 * @author Julien Le Dem
 *
 */
public abstract class BytePacker {

  private final int bitWidth;

  BytePacker(int bitWidth) {
    this.bitWidth = bitWidth;
  }

  /**
   * @return the width in bits used for encoding, also how many bytes are packed/unpacked at a time by pack8Values/unpack8Values
   */
  public final int getBitWidth() {
    return bitWidth;
  }

  /**
   * pack 8 values from input at inPos into bitWidth bytes in output at outPos.
   * nextPosition: inPos += 8; outPos += getBitWidth()
   * @param input the input values
   * @param inPos where to read from in input
   * @param output the output bytes
   * @param outPos where to write to in output
   */
  public abstract void pack8Values(final int[] input, final int inPos, final byte[] output, final int outPos);

  /**
   * pack 32 values from input at inPos into bitWidth * 4 bytes in output at outPos.
   * nextPosition: inPos += 32; outPos += getBitWidth() * 4
   * @param input the input values
   * @param inPos where to read from in input
   * @param output the output bytes
   * @param outPos where to write to in output
   */
  public abstract void pack32Values(int[] input, int inPos, byte[] output, int outPos);

  /**
   * unpack bitWidth bytes from input at inPos into 8 values in output at outPos.
   * nextPosition: inPos += getBitWidth(); outPos += 8
   * @param input the input bytes
   * @param inPos where to read from in input
   * @param output the output values
   * @param outPos where to write to in output
   */
  public abstract void unpack8Values(final byte[] input, final int inPos, final int[] output, final int outPos);

  /**
   * unpack bitWidth * 4 bytes from input at inPos into 32 values in output at outPos.
   * nextPosition: inPos += getBitWidth() * 4; outPos += 32
   * @param input the input bytes
   * @param inPos where to read from in input
   * @param output the output values
   * @param outPos where to write to in output
   */
  public abstract void unpack32Values(byte[] input, int inPos, int[] output, int outPos);

}