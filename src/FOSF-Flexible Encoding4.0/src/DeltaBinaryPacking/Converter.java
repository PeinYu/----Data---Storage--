package DeltaBinaryPacking;
/*
 * adapt from  parquet
 *
 */

public  abstract class Converter {
	  abstract public boolean isPrimitive();

	  public PrimitiveConverter asPrimitiveConverter() {
	    throw new ClassCastException(getClass().getName());
	  }

	  public GroupConverter asGroupConverter() {
	    throw new ClassCastException(getClass().getName());
	  }

	}