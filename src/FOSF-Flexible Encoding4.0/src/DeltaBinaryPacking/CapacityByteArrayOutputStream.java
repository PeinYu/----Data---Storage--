package DeltaBinaryPacking;
/*
 * adapt from  parquet
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * functionality of ByteArrayOutputStream without the memory and copy overhead
 *
 * It will linearly create a new slab of the initial size when needed (instead of creating a new buffer and copying the data).
 * After 10 slabs their size will increase exponentially (similar to {@link ByteArrayOutputStream} behavior) by making the new slab size the size of the existing data.
 *
 * When reusing a buffer it will adjust the slab size based on the previous data size ({@link CapacityByteArrayOutputStream#reset()})
 *
 * @author Julien Le Dem
 *
 */
public class CapacityByteArrayOutputStream extends OutputStream {
  private static final Log LOG = Log.getLog(CapacityByteArrayOutputStream.class);

  private static final int MINIMUM_SLAB_SIZE = 64 * 1024;
  private static final int EXPONENTIAL_SLAB_SIZE_THRESHOLD = 10;

  private int slabSize;
  private List<byte[]> slabs = new ArrayList<byte[]>();
  private byte[] currentSlab;
  private int capacity;
  private int currentSlabIndex;
  private int currentSlabPosition;
  private int size;

  /**
   * @param initialSize the initialSize of the buffer (also slab size)
   */
  public CapacityByteArrayOutputStream(int initialSize) {
    Preconditions.checkArgument(initialSize > 0, "initialSize must be > 0");
    initSlabs(initialSize);
  }

  private void initSlabs(int initialSize) {
    if (Log.DEBUG) LOG.debug(String.format("initial slab of size %d", initialSize));
    this.slabSize = initialSize;
    this.slabs.clear();
    this.capacity = initialSize;
    this.currentSlab = new byte[slabSize];
    this.slabs.add(currentSlab);
    this.currentSlabIndex = 0;
    this.currentSlabPosition = 0;
    this.size = 0;
  }

  private void addSlab(int minimumSize) {
    this.currentSlabIndex += 1;
    if (currentSlabIndex < this.slabs.size()) {
      // reuse existing slab
      this.currentSlab = this.slabs.get(currentSlabIndex);
      if (Log.DEBUG) LOG.debug(String.format("reusing slab of size %d", currentSlab.length));
      if (currentSlab.length < minimumSize) {
        if (Log.DEBUG) LOG.debug(String.format("slab size %,d too small for value of size %,d. replacing slab", currentSlab.length, minimumSize));
        byte[] newSlab = new byte[minimumSize];
        capacity += minimumSize - currentSlab.length;
        this.currentSlab = newSlab;
        this.slabs.set(currentSlabIndex, newSlab);
      }
    } else {
      if (currentSlabIndex > EXPONENTIAL_SLAB_SIZE_THRESHOLD) {
        // make slabs bigger in case we are creating too many of them
        // double slab size every time.
        this.slabSize = size;
        if (Log.DEBUG) LOG.debug(String.format("used %d slabs, new slab size %d", currentSlabIndex, slabSize));
      }
      if (slabSize < minimumSize) {
        if (Log.DEBUG) LOG.debug(String.format("slab size %,d too small for value of size %,d. Bumping up slab size", slabSize, minimumSize));
        this.slabSize = minimumSize;
      }
      if (Log.DEBUG) LOG.debug(String.format("new slab of size %d", slabSize));
      this.currentSlab = new byte[slabSize];
      this.slabs.add(currentSlab);
      this.capacity += slabSize;
    }
    this.currentSlabPosition = 0;
  }

  @Override
  public void write(int b) {
    if (currentSlabPosition == currentSlab.length) {
      addSlab(1);
    }
    currentSlab[currentSlabPosition] = (byte) b;
    currentSlabPosition += 1;
    size += 1;
  }

  @Override
  public void write(byte b[], int off, int len) {
    if ((off < 0) || (off > b.length) || (len < 0) ||
        ((off + len) - b.length > 0)) {
      throw new IndexOutOfBoundsException();
    }
    if (currentSlabPosition + len >= currentSlab.length) {
      final int length1 = currentSlab.length - currentSlabPosition;
      System.arraycopy(b, off, currentSlab, currentSlabPosition, length1);
      final int length2 = len - length1;
      addSlab(length2);
      System.arraycopy(b, off + length1, currentSlab, currentSlabPosition, length2);
      currentSlabPosition = length2;
    } else {
      System.arraycopy(b, off, currentSlab, currentSlabPosition, len);
      currentSlabPosition += len;
    }
    size += len;
  }

  /**
   * Writes the complete contents of this buffer to the specified output stream argument. the output
   * stream's write method <code>out.write(slab, 0, slab.length)</code>) will be called once per slab.
   *
   * @param      out   the output stream to which to write the data.
   * @exception  IOException  if an I/O error occurs.
   */
  public void writeTo(OutputStream out) throws IOException {
    for (int i = 0; i < currentSlabIndex; i++) {
      final byte[] slab = slabs.get(i);
      out.write(slab, 0, slab.length);
    }
   // System.out.println(out.getClass());
    out.write(currentSlab, 0, currentSlabPosition);
//  File file=  new File("/home/wangmeng/file");
//  if(file.exists()){
//	  f
//  }
    //added  by  me
//    FileOutputStream fos;
//    File file=new   File("/home/wangmeng/encodingFile");
//    if(file.exists()){
//    	fos =new  FileOutputStream(new File("/home/wangmeng/dictionaryFile"));
//    }
//    else{
//    fos =new  FileOutputStream(file);
//    }
//    //added  by me
//    ((ByteArrayOutputStream) out).writeTo(fos);
//    fos.close();
//    //added  by me 
//    out.close();
  }

  /**
   * @return the size of the allocated buffer
   */
  public int getCapacity() {
    return capacity;
  }

  /**
   * When re-using an instance with reset, it will adjust slab size based on previous data size.
   * The intent is to reuse the same instance for the same type of data (for example, the same column).
   * The assumption is that the size in the buffer will be consistent. Otherwise we fall back to exponentialy double the slab size.
   * <ul>
   * <li>if we used less than half of the first slab (and it is above the minimum slab size), we will make the slab size smaller.
   * <li>if we used more than the slab count threshold (10), we will re-adjust the slab size.
   * </ul>
   * if re-adjusting the slab size we will make it 1/5th of the previous used size in the buffer so that overhead of extra memory allocation is about 20%
   * If we used less than the available slabs we free up the unused ones to reduce memory overhead.
   */
  public void reset() {
    // heuristics to adjust slab size
    if (
        // if we have only one slab, make sure it is not way too big (more than twice what we need). Except if the slab is already small
        (currentSlabIndex == 0 && currentSlabPosition < currentSlab.length / 2 && currentSlab.length > MINIMUM_SLAB_SIZE)
        ||
        // we want to avoid generating too many slabs.
        (currentSlabIndex > EXPONENTIAL_SLAB_SIZE_THRESHOLD)
        ){
      // readjust slab size
      initSlabs(Math.max(size / 5, MINIMUM_SLAB_SIZE)); // should make overhead to about 20% without incurring many slabs
      if (Log.DEBUG) LOG.debug(String.format("used %d slabs, new slab size %d", currentSlabIndex + 1, slabSize));
    } else if (currentSlabIndex < slabs.size() - 1) {
      // free up the slabs that we are not using. We want to minimize overhead
      this.slabs = new ArrayList<byte[]>(slabs.subList(0, currentSlabIndex + 1));
      this.capacity = 0;
      for (byte[] slab : slabs) {
        capacity += slab.length;
      }
    }
    this.currentSlabIndex = 0;
    this.currentSlabPosition = 0;
    this.currentSlab = slabs.get(currentSlabIndex);
    this.size = 0;
  }

  /**
   * @return the size of the buffered data
   */
  public long size() {
    return size;
  }

  /**
   * @return the index of the last value being written to this stream, which
   * can be passed to {@link #setByte(long, byte)} in order to change it
   */
  public long getCurrentIndex() {
    Preconditions.checkArgument(size > 0, "This is an empty stream");
    return size - 1;
  }

  /**
   * Replace the byte stored at position index in this stream with value
   *
   * @param index which byte to replace
   * @param value the value to replace it with
   */
  public void setByte(long index, byte value) {
    Preconditions.checkArgument(index < size,
      "Index: " + index + " is >= the current size of: " + size);

    long seen = 0;
    for (int i = 0; i <=currentSlabIndex; i++) {
      byte[] slab = slabs.get(i);
      if (index < seen + slab.length) {
        // ok found index
        slab[(int)(index-seen)] = value;
        break;
      }
      seen += slab.length;
    }
  }

  /**
   * @param prefix  a prefix to be used for every new line in the string
   * @return a text representation of the memory usage of this structure
   */
  public String memUsageString(String prefix) {
    return String.format("%s %s %d slabs, %,d bytes", prefix, getClass().getSimpleName(), slabs.size(), getCapacity());
  }

  /**
   * @return the total count of allocated slabs
   */
  int getSlabCount() {
    return slabs.size();
  }
}