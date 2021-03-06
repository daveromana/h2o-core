package water.fvec;

import water.AutoBuffer;
import water.Futures;
import water.H2O;
import water.MemoryManager;
import water.fvec.AppendableVec;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.PrettyPrint;
import water.util.StringUtils;
import water.util.UnsafeUtils;

import java.util.*;

// An uncompressed chunk of data, supporting an append operation
public class NewChunk extends Chunk {

  public void alloc_mantissa(int sparseLen) {_ms = new Mantissas(sparseLen);}

  public void alloc_exponent(int sparseLen) {_xs = new Exponents(sparseLen);}

  public int is(int i) { return _is[i];}

  public void set_is(int i, int val) {_is[i] = val;}

  public void alloc_nums(int len) { _ms = new Mantissas(len); _xs = new Exponents(len);}


  /**
   * Wrapper around exponent, stores values (only if there are non-zero exponents) in bytes or ints.
   */
  public static class Exponents {
    int _len;
    public Exponents(int cap){_len = cap;}
    byte [] _vals1;
    int  [] _vals4;
    private void alloc_data(int val){
      byte b = (byte)val;
      if(b == val && b != CATEGORICAL_1) {
        _vals1 = MemoryManager.malloc1(_len);
        }
      else
        _vals4 = MemoryManager.malloc4(_len);
    }

    public void set(int idx, int x) {
      if((_vals1 == null && _vals4 == null) && (x==0)){
    	  alloc_data(x);
    	  return;
      }else if((_vals1 != null)&&(x == b && b > Byte.MIN_VALUE-1)){
        byte b = (byte)x;
        _vals1[idx] = b;
        } else {
          // need to switch to 4 byte values
          int len = _vals1.length;
          _vals4 = MemoryManager.malloc4(len);
          for (int i = 0; i < _vals1.length; ++i){
            _vals4[i] = (_vals1[i] == CATEGORICAL_1)?CATEGORICAL_2:_vals1[i];
          _vals1 = null;
          _vals4[idx] = x;
        }
        }
    }
    
    public int get(int id){
      if(_vals1 == null && null == _vals4) {
    	  return 0;
      }
      if(_vals1 != null) {
        int x = _vals1[id];
        if(x == CATEGORICAL_1) {
          x = CATEGORICAL_2;
          }
        return x;
      }
      return _vals4[id];
    }
    public boolean isCategorical(int i) { return _vals1 !=  null && _vals1[i] == CATEGORICAL_1 || _vals4 != null && _vals4[i] == CATEGORICAL_2;}

    private static byte CATEGORICAL_1 = Byte.MIN_VALUE;
    private static int  CATEGORICAL_2 = Integer.MIN_VALUE;

    public void setCategorical(int idx) {
      if(_vals1 == null && _vals4 == null) {
        alloc_data(0);
        }
      if(_vals1 != null) {
    	  _vals1[idx] = CATEGORICAL_1;
      }
      else _vals4[idx] = CATEGORICAL_2;
    }

    public void move(int to, int from) {
      if(_vals1 == null && null == _vals4) {
    	  return;
      }
      if(_vals1 != null) {
        _vals1[to] = _vals1[from];
        }
      else
        _vals4[to] = _vals4[from];
    }

    public void resize(int len) {
      if (_vals1 != null) {
    	  _vals1 = Arrays.copyOf(_vals1, len);
      }
      else if (_vals4 != null) {
    	  _vals4 = Arrays.copyOf(_vals4, len);
      }
      _len = len;
    }
  }

  /**
   * Class wrapping around mantissa.
   * Stores values in bytes, ints or longs, if data fits.
   * Sets and gets done in longs.
   */
  public static class Mantissas {
    byte [] _vals1;
    int  [] _vals4;
    long [] _vals8;
    int _nzs;

    public Mantissas(int cap) {_vals1 = MemoryManager.malloc1(cap);}

    public void set(int idx, long l) {
      long old;
      if((_vals1 != null)||(_vals4 != null)) { // check if we fit withing single byte
        byte b = (byte)l;
        int i = (int)l;        
        if((b == l)||(i == l)) {
            switchToInts();
            old = _vals4[idx];
            vals4[idx] = i;
            old = _vals1[idx];
          _vals1[idx] = b;
        } else {
            switchToLongs();
            old = _vals8[idx];
            _vals8[idx] = l;
          }
        }
      }     


public long get(int id) {
      if(_vals1 != null) {
    	  return _vals1[id];
      }
      if(_vals4 != null) {
    	  return _vals4[id];
      }
      return _vals8[id];
    }

    public void switchToInts() {
      int len = _vals1.length;
      _vals4 = MemoryManager.malloc4(len);
      for(int i = 0; i < _vals1.length; ++i)
        _vals4[i] = _vals1[i];
      _vals1 = null;
    }

    public void switchToLongs() {
      int len = Math.max(_vals1 == null?0:_vals1.length,_vals4 == null?0:_vals4.length);
      int newlen = len;
      _vals8 = MemoryManager.malloc8(newlen);
      if(_vals1 != null) {
        for(int i = 0; i < _vals1.length; ++i)
          _vals8[i] = _vals1[i];
      }
      else if(_vals4 != null) {
        for(int i = 0; i < _vals4.length; ++i)
          _vals8[i] = _vals4[i];
      }
      _vals1 = null;
      _vals4 = null;
    }

    public void move(int to, int from) {
      if(to != from) {
        if (_vals1 != null) {
          _vals1[to] = _vals1[from];
          _vals1[from] = 0;
        } else if (_vals4 != null) {
          _vals4[to] = _vals4[from];
          _vals4[from] = 0;
        } else {
          _vals8[to] = _vals8[from];
          _vals8[from] = 0;
        }
      }
    }

    public int len() {
      return _vals1 != null?_vals1.length:_vals4 != null?_vals4.length:_vals8.length;
    }

    public void resize(int len) {
      if(_vals1 != null) {
    	  _vals1 = Arrays.copyOf(_vals1,len);
      }
      else if(_vals4 != null) {
    	  _vals4 = Arrays.copyOf(_vals4,len);
      }
      else if(_vals8 != null) {
    	  _vals8 = Arrays.copyOf(_vals8,len);
      }
    }
  }

  public final int _cidx;
  // We can record the following (mixed) data types:
  // 1- doubles, in _ds including NaN for NA & 0; _ls==_xs==null
  // 2- scaled decimals from parsing, in _ls & _xs; _ds==null
  // 3- zero: requires _ls==0 && _xs==0
  // 4- NA: _ls==Long.MAX_VALUE && _xs==Integer.MIN_VALUE || _ds=NaN
  // 5- Categorical: _xs==(Integer.MIN_VALUE+1) && _ds==null
  // 6- Str: _ss holds appended string bytes (with trailing 0), _is[] holds offsets into _ss[]
  // Chunk._len is the count of elements appended
  // Sparse: if _sparseLen != _len, then _ls/_ds are compressed to non-zero's only,
  // and _xs is the row number.  Still _len is count of elements including
  // zeros, and _sparseLen is count of non-zeros.
  protected transient Mantissas _ms;   // Mantissa
  protected transient BitSet   _missing;
  protected transient Exponents _xs;   // Exponent, or if _ls==0, NA or Categorical or Rows
  private transient int    _id[];   // Indices (row numbers) of stored values, used for sparse
  private transient double _ds[];   // Doubles, for inflating via doubles
  public transient byte[]   _ss;   // Bytes of appended strings, including trailing 0
  private transient int    _is[];   // _is[] index of strings - holds offsets into _ss[]. _is[i] == -1 means NA/sparse

  int   [] alloc_indices(int l)  { return _id = MemoryManager.malloc4(l); }
  public double[] alloc_doubles(int l)  {
    _ms = null;
    _xs = null;
    _missing = null;
    return _ds = MemoryManager.malloc8d(l);
  }
  int   [] alloc_str_indices(int l) {
    _ms = null;
    _xs = null;
    _missing = null;
    _ds = null;
    return _is = MemoryManager.malloc4(l);
  }

  final protected int   []  indices() { return _id; }
  final protected double[]  doubles() { return _ds; }

  @Override public boolean isSparseZero() { return sparseZero(); }
  public boolean _sparseNA = false;
  @Override public boolean isSparseNA() {return sparseNA();}
  void setSparseNA() {_sparseNA = true;}

  public int _sslen;                   // Next offset into _ss for placing next String

  public int _sparseLen;
  int set_sparseLen(int l) {
    return this._sparseLen = l;
  }
  @Override public int sparseLenZero() { return _sparseNA ? _len : _sparseLen;}
  @Override public int sparseLenNA() { return _sparseNA ? _sparseLen : _len; }

  private int _naCnt=-1;                // Count of NA's   appended
  protected int naCnt() { return _naCnt; }               // Count of NA's   appended
  private int _catCnt;                  // Count of Categorical's appended
  private int _strCnt;                  // Count of string's appended
  private int _nzCnt;                   // Count of non-zero's appended
  private int _uuidCnt;                 // Count of UUIDs

  public int _timCnt = 0;
  protected static final int MIN_SPARSE_RATIO = 8;
  private int _sparseRatio = MIN_SPARSE_RATIO;
  public boolean _isAllASCII = true; //For cat/string col, are all characters in chunk ASCII?

  public NewChunk( Vec vec, int cidx ) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(4);
    _xs = new Exponents(4);
  }

  public NewChunk( Vec vec, int cidx, boolean sparse ) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(4);
    _xs = new Exponents(4);
    if(sparse) {
    	_id = new int[4];
    }

  }

  public NewChunk(double [] ds) {
    _cidx = -1;
    _vec = null;
    setDoubles(ds);
  }
  public NewChunk( Vec vec, int cidx, long[] mantissa, int[] exponent, int[] indices, double[] doubles) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(mantissa.length);
    _xs = new Exponents(exponent.length);
    for(int i = 0; i < mantissa.length; ++i) {
      _ms.set(i,mantissa[i]);
      _xs.set(i,exponent[i]);
    }
    _id = indices;
    _ds = doubles;
    if (_ms != null && _sparseLen==0) {
    	set_sparseLen(set_len(mantissa.length));
    }
    if (_ds != null && _sparseLen==0) {
    	set_sparseLen(set_len(_ds.length));
    }
    if (_id != null && _sparseLen==0) {
    	set_sparseLen(_id.length);
    }
  }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk c ) {
    this(c._vec, c.cidx());
    _start = c._start;
  }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk c, double [] vals) {
    _vec = c._vec; _cidx = c.cidx();
    _start = c._start;
    _ds = vals;
    _sparseLen = _len = _ds.length;
  }

  // Pre-sized newchunks.
  public NewChunk( Vec vec, int cidx, int len ) {
    this(vec,cidx);
    _ds = new double[len];
    Arrays.fill(_ds, Double.NaN);
    set_sparseLen(set_len(len));
  }

  public NewChunk setSparseRatio(int s) {
    _sparseRatio = s;
    return this;
  }

  public void setDoubles(double[] ds) {
    _ds = ds;
    _sparseLen = _len = ds.length;
    _ms = null;
    _xs = null;
  }

  public void set_vec(Vec vec) { _vec = vec; }


  public final class Value {
    int _gId; // row number in dense (ie counting zeros)
    int _lId; // local array index of this value, equal to _gId if dense

    public Value(int lid, int gid){_lId = lid; _gId = gid;}
    public final int rowId0(){return _gId;}
    public void add2Chunk(NewChunk c){add2Chunk_impl(c,_lId);}
  }

  private transient BufferedString _bfstr = new BufferedString();

  private void add2Chunk_impl(NewChunk c, int i) {
    if ((isNA2(i))||(isUUID())||(_ms != null)||(_ds != null)||(_ss != null)) {
      c.addNA();
      c.addUUID(_ms.get(i), Double.doubleToRawLongBits(_ds[i]));
      c.addNum(_ms.get(i), _xs.get(i));
      c.addNum(_ds[i]);
      int sidx = _is[i];
      int nextNotNAIdx = i + 1;
      // Find next not-NA value (_is[idx] != -1)
      while (nextNotNAIdx < _is.length && _is[nextNotNAIdx] == -1) nextNotNAIdx++;
      int send = nextNotNAIdx < _is.length ? _is[nextNotNAIdx]: _sslen;
      int slen = send - sidx -1 /*account for trailing zero byte*/;
      assert slen >= 0 : getClass().getSimpleName() + ".add2Chunk_impl: slen=" + slen + ", sidx=" + sidx + ", send=" + send;
      // null-BufferedString represents NA value
      BufferedString bStr = sidx == -1 ? null : _bfstr.set(_ss, sidx, slen);
      c.addStr(bStr);
    } else
      throw new IllegalStateException();
  }
  
  public void add2Chunk(NewChunk c, int i){
    if(!isSparseNA() && !isSparseZero()) {
      add2Chunk_impl(c,i);
      }
    else {
      int j = Arrays.binarySearch(_id,0,_sparseLen,i);
      if(j >= 0) {
        add2Chunk_impl(c,j);
        }
      else if(isSparseNA()) {
        c.addNA();
      }
      else
        c.addNum(0,0);
    }
  }

  // Heuristic to decide the basic type of a column
  byte type() {
           // No rollups yet?
      int nas=0, es=0, nzs=0, ss=0;
      if(( _ds != null && _ms != null )||(_sparseLen > 0) || ( _is != null ) ) { // UUID?
    	  assert _xs==null;
        _uuidCnt = _len -nas;
      }else if((_naCnt == _len)||(_strCnt > 0)
    	||(_catCnt > 0 && _catCnt + _naCnt + (isSparseZero()? _len-_sparseLen : 0) == _len)
    	||( _uuidCnt > 0 ))  {        // All NAs ==> NA Chunk
      return Vec.T_BAD;
      return Vec.T_STR;
      return Vec.T_CAT;
      return Vec.T_UUID;
      }else{
    	  ss++;}
    // Larger of time & numbers
    int nums = _len -_naCnt-_timCnt;
    return _timCnt >= nums ? Vec.T_TIME : Vec.T_NUM;
  }

  //what about sparse reps?
  protected final boolean isNA2(int idx) {
    if (isString()) {
    	return _is[idx] == -1;
    }
    if(isUUID() || _ds == null) {
    	return _missing != null && _missing.get(idx);
    }
    return Double.isNaN(_ds[idx]);
  }
  protected final boolean isCategorical2(int idx) {
    return _xs!=null && _xs.isCategorical(idx);
  }
  protected final boolean isCategorical(int idx) {
    if(_id == null) {
    	return isCategorical2(idx);
    }
    int j = Arrays.binarySearch(_id,0, _sparseLen,idx);
    return j>=0 && isCategorical2(j);
  }

  public void addCategorical(int e) {
    if(_ms == null || _ms.len() == _sparseLen) {
      append2slow();
      }
    if( e != 0 || !isSparseZero() ) {
      _ms.set(_sparseLen,e);
      _xs.setCategorical(_sparseLen);
      if(_id != null) {
    	  _id[_sparseLen] = _len;
      }
      ++_sparseLen;
    }
    ++_len;
  }
  public void addNA() {
	    if((!_sparseNA)&&(isString()) ||(_missing == null) ) {
	        addStr(null);
	        BitSet missing = new BitSet();
	        missing = new BitSet();
	        _missing.set(_sparseLen);
	        return;
	      } else if ((isUUID())&&( _ms==null || _ds== null || _sparseLen >= _ms.len())) {
	          append2slowUUID();
	          _id[_sparseLen] = _len;
		        _ds[_sparseLen] = Double.NaN;
		        ++_sparseLen;

	        } else if (_ds != null) {
	        addNum(Double.NaN);
	        return;
	      } else {
	          append2slow();
	    }
	    ++_len;
	  }
  
  public void addNum (long val, int exp) {
    if( isUUID() || isString() ) {
      addNA();
    } else if(_ds != null) {
      assert _ms == null;
      addNum(PrettyPrint.pow10(val,exp));
    } else if((val != 0 || !isSparseZero()) && (_ms == null || _ms.len() == _sparseLen)) {
          append2slow();
          addNum(val, exp); // sparsity could've changed
          return;
        }else{
            long t;                // Remove extra scaling
            while (exp < 0 && exp > -9999999 && (t = val / 10) * 10 == val) {
            val = t;
            exp++;
            
          }
            int len = _ms.len();
            int slen = _sparseLen;

            _ms.set(_sparseLen, val);
            _xs.set(_sparseLen, exp);
            assert _id == null || _id.length == _ms.len() : "id.len = " + _id.length + ", ms.len = " + _ms.len() + ", old ms.len = " + len + ", sparseLen = " + slen;
            _sparseLen++;
          _len++;
        }
  }
  
  // Fast-path append double data
  public void addNum(double d) {
    if( isUUID() || isString() ) { addNA(); return; }
    boolean predicate = _sparseNA ? !Double.isNaN(d) : isSparseZero()?d != 0:true;
   // while((predicate)&&((long)d == d)){
     //     addNum((long)d,0);
       //   return;
         // switch_to_doubles();
        //}
      //if ds not big enough
      if(_sparseLen == _ds.length ) {
        append2slowd();
        // call addNum again since append2slowd might have flipped to sparse
        addNum(d);
        assert _sparseLen <= _len;
        return;
      }else  {
    	  _id[_sparseLen] = _len;
      }
      _ds[_sparseLen] = d;
      _sparseLen++;
      _len++;
      assert _sparseLen <= _len;
  }

  private void append_ss(String str) {
    byte[] bytes = str == null ? new byte[0] : StringUtils.bytesOf(str);

    // Allocate memory if necessary
    if (_ss == null) {
      _ss = MemoryManager.malloc1((bytes.length+1) * 4);
      }
    while (_ss.length < (_sslen + bytes.length+1))
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);

    // Copy bytes to _ss
    for (byte b : bytes) _ss[_sslen++] = b;
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }

  private void append_ss(BufferedString str) {
    int strlen = str.length();
    int off = str.getOffset();
    byte b[] = str.getBuffer();

    if (_ss == null) {
      _ss = MemoryManager.malloc1((strlen + 1) * 4);
    }
    while (_ss.length < (_sslen + strlen + 1)) {
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);
    }
    for (int i = off; i < off+strlen; i++)
      _ss[_sslen++] = b[i];
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }

  // Append a string, store in _ss & _is
  // TODO cleanup
  public void addStr(Object str) {
    if(_id == null || str != null) {
      if(_is == null || _sparseLen >= _is.length) {
        append2slowstr();
        addStr(str);
        assert _sparseLen <= _len;
        return;
        _is[_sparseLen] = _sslen;
        _sparseLen++;
      }else // this spares some callers from an unneeded conversion to BufferedString first
          append_ss((String) str);
      } 
    else if (_id == null) {
        set_sparseLen(_sparseLen + 1);
      }
    assert _sparseLen <= _len;
  }

  // TODO: FIX isAllASCII test to actually inspect string contents
  public void addStr(Chunk c, long row) {
    if( c.isNA_abs(row) ) {
    	addNA();
    }
    else { addStr(c.atStr_abs(new BufferedString(), row)); _isAllASCII &= ((CStrChunk)c)._isAllASCII; }
  }


  public void addStr(Chunk c, int row) {
    if( c.isNA(row) ) {
    	addNA();
    }
    else { addStr(c.atStr(new BufferedString(), row)); _isAllASCII &= ((CStrChunk)c)._isAllASCII; }
  }

  public void addUUID(UUID uuid) {
    if (uuid == null) {
    	addNA();
    }
    else addUUID(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
  }

  // Append a UUID, stored in _ls & _ds
  public void addUUID( long lo, long hi ) {
    if (C16Chunk.isNA(lo, hi)) {
    	throw new IllegalArgumentException("Cannot set illegal UUID value");
    }
    if( _ms==null || _ds== null || _sparseLen >= _ms.len() ) {
      append2slowUUID();
      }
    _ms.set(_sparseLen,lo);
    _ds[_sparseLen] = Double.longBitsToDouble(hi);
    if (_id != null) {
    	_id[_sparseLen] = _len;
    }
    _sparseLen++;
    _len++;
    assert _sparseLen <= _len;
  }
  public void addUUID( Chunk c, long row ) {
    if (c.isNA_abs(row)) {
    	addNA();
    }
    else addUUID(c.at16l_abs(row),c.at16h_abs(row));
  }
  public void addUUID( Chunk c, int row ) {
    if( c.isNA(row) ) {
    	addNA();
    }
    else addUUID(c.at16l(row),c.at16h(row));
  }

  public final boolean isUUID(){return _ms != null && _ds != null; }
  public final boolean isString(){return _is != null; }
  public final boolean sparseZero(){return _id != null && !_sparseNA;}
  public final boolean sparseNA() {return _id != null && _sparseNA;}

  public void addZeros(int n){
    if(n == 0) {
    	return;
    }
    assert n > 0;
    while(!sparseZero() && n != 0) {
      addNum(0, 0);
      n--;
    }
    assert n >= 0;
    _len += n;
  }
  
  public void addNAs(int n) {
    if(n == 0) {
    	return;
    }
    while(!sparseNA() && n != 0) {
      addNA();
      n--;
    }
    _len += n;
  }
  
  // Append all of 'nc' onto the current NewChunk.  Kill nc.
  public void add( NewChunk nc ) {
    assert _cidx >= 0;
    assert _sparseLen <= _len;
    assert nc._sparseLen <= nc._len :"_sparseLen = " + nc._sparseLen + ", _len = " + nc._len;
    if(_len == 0){
      _ms = nc._ms; nc._ms = null;
      _xs = nc._xs; nc._xs = null;
      _id = nc._id; nc._id = null;
      _ds = nc._ds; nc._ds = null;
      _is = nc._is; nc._is = null;
      _ss = nc._ss; nc._ss = null;
      set_sparseLen(nc._sparseLen);
      set_len(nc._len);
      return;
    }else if(nc.sparseZero() != sparseZero() || nc.sparseNA() != sparseNA()){ // for now, just make it dense
      cancel_sparse();
      nc.cancel_sparse();
    }else if(_id != null) {
      assert nc._id != null;
      _id = MemoryManager.arrayCopyOf(_id,_sparseLen + nc._sparseLen);
      System.arraycopy(nc._id,0,_id, _sparseLen, nc._sparseLen);
      for(int i = _sparseLen; i < _sparseLen + nc._sparseLen; ++i) _id[i] += _len;
    } else assert nc._id == null;

    set_sparseLen(_sparseLen + nc._sparseLen);
    set_len(_len + nc._len);
    nc._ms = null;  nc._xs = null; nc._id = null; nc.set_sparseLen(nc.set_len(0));
    assert _sparseLen <= _len;
  }

  // Fast-path append long data
//  void append2( long l, int x ) {
//    boolean predicate = _sparseNA ? (l != Long.MAX_VALUE || x != Integer.MIN_VALUE): l != 0;
//    if(_id == null || predicate){
//      if(_ms == null || _sparseLen == _ms._c) {
//        append2slow();
//        // again call append2 since calling append2slow might have changed things (eg might have switched to sparse and l could be 0)
//        append2(l,x);
//        return;
//      }
//      _ls[_sparseLen] = l;
//      _xs[_sparseLen] = x;
//      if(_id  != null)_id[_sparseLen] = _len;
//      set_sparseLen(_sparseLen + 1);
//    }
//    set_len(_len + 1);
//    assert _sparseLen <= _len;
//  }

  // Slow-path append data
  private void append2slowd() {
    assert _ms==null;
    int nonnas = 0;
    if((_ds != null && _ds.length > 0)&&((nonnas+1)*_sparseRatio < _len)){
          set_sparse(nonnas,Compress.NA);
          assert _sparseLen == 0 || _sparseLen <= _ds.length:"_sparseLen = " + _sparseLen + ", _ds.length = " + _ds.length + ", nonnas = " + nonnas +  ", len = " + _len;
          assert _id.length == _ds.length;
          assert _sparseLen <= _len;
          return;
        }else {
        // verify we're still sufficiently sparse
        	 set_sparse(nzs,Compress.ZERO);
             assert _sparseLen == 0 || _sparseLen <= _ds.length:"_sparseLen = " + _sparseLen + ", _ds.length = " + _ds.length + ", nzs = " + nzs +  ", len = " + _len;
             assert _id.length == _ds.length;
             assert _sparseLen <= _len;
             return;
        	cancel_sparse();
        	alloc_indices(4);
      }
      _ds = MemoryManager.arrayCopyOf(_ds, _sparseLen << 1);
    assert _sparseLen == 0 || _ds.length > _sparseLen :"_ds.length = " + _ds.length + ", _sparseLen = " + _sparseLen;
    assert _id == null || _id.length == _ds.length;
    assert _sparseLen <= _len;
  }
  
  // Slow-path append data
  private void append2slowUUID() {
    if( _ds==null && _ms!=null ) { // This can happen for columns with all NAs and then a UUID
      _xs=null;
      _ms.switchToLongs();
      _ds = MemoryManager.malloc8d(_sparseLen);
      Arrays.fill(_ms._vals8,C16Chunk._LO_NA);
      Arrays.fill(_ds,Double.longBitsToDouble(C16Chunk._HI_NA));
    }else if( _ms != null && _sparseLen > 0 ) {
      _ds = MemoryManager.arrayCopyOf(_ds, _sparseLen * 2);
      _ms.resize(_sparseLen*2);
    } else {
      _ms = new Mantissas(4);
      _xs = null;
      _ms.switchToLongs();
      _ds = new double[4];
      cancel_sparse();
      _id = Arrays.copyOf(_id,_sparseLen*2);
    }
  }
  // Slow-path append string
  private void append2slowstr() {
    int nzs = 0;
	// In case of all NAs and then a string, convert NAs to string NAs
    if((_is != null && _is.length > 0) || ( (nzs+1)*_sparseRatio < _len) ){
      // Check for sparseness       
      } else if((_sparseRatio*(_sparseLen) >> 2) > _len)  {
        	cancel_sparse();
        }
        else{
        	_id = MemoryManager.arrayCopyOf(_id,_sparseLen<<1);
        	  // assume one non-null for the element currently being stored
             set_sparse(nzs, Compress.ZERO);
      }
      _is = MemoryManager.arrayCopyOf(_is, _sparseLen<<1);
      /* initialize the memory extension with -1s */
      for (int i = _sparseLen; i < _is.length; i++) _is[i] = -1;
      {
	      _is = MemoryManager.malloc4 (4);
	      _xs = null; _ms = null;
	      alloc_str_indices(_sparseLen);
	      Arrays.fill(_is,-1);
	        /* initialize everything with -1s */
    }
    assert _sparseLen == 0 || _is.length > _sparseLen:"_ls.length = " + _is.length + ", _len = " + _sparseLen;
  }
  // Slow-path append data
  private void append2slow( ) {
// PUBDEV-2639 - don't die for many rows, few columns -> can be long chunks
//    if( _sparseLen > FileVec.DFLT_CHUNK_SIZE )
//      throw new ArrayIndexOutOfBoundsException(_sparseLen);
    assert _ds==null;
    if((_ms != null && _sparseLen > 0)&&(_id == null)) { // check for sparseness
        int nzs = _ms._nzs + (_missing != null?_missing.cardinality():0);
        int nonnas = _sparseLen - ((_missing != null)?_missing.cardinality():0);
        if((nonnas+1)*_sparseRatio < _len) {
          set_sparse(nonnas,Compress.NA);
          assert _id.length == _ms.len():"id.len = " + _id.length + ", ms.len = " + _ms.len();
          assert _sparseLen <= _len;
          return;        
        } else if((nzs+1)*_sparseRatio < _len) { // note order important here
          set_sparse(nzs,Compress.ZERO);
          assert _sparseLen <= _len;
          assert _sparseLen == nzs;
          return;
        }else {
        // verify we're still sufficiently sparse
        	cancel_sparse();
        	_id = MemoryManager.arrayCopyOf(_id, _id.length*2); 
      _ms.resize(_sparseLen*2);
      _xs.resize(_sparseLen*2);
      _ms = new Mantissas(16);
      _xs = new Exponents(16);
    	  _id = new int[16];
      }
    }
    assert _sparseLen <= _len;
  }

  // Do any final actions on a completed NewVector.  Mostly: compress it, and
  // do a DKV put on an appropriate Key.  The original NewVector goes dead
  // (does not live on inside the K/V store).
  public Chunk new_close() {
    Chunk chk = compress();
    if(_vec instanceof AppendableVec) {
      ((AppendableVec)_vec).closeChunk(_cidx,chk._len);
      }
    return chk;
  }
  public void close(Futures fs) { close(_cidx,fs); }

  private void switch_to_doubles(){
    assert _ds == null;
    double [] ds = MemoryManager.malloc8d(_sparseLen);
    for(int i = 0; i < _sparseLen; ++i)
      ds[i] = getDouble(i);
    _ms = null;
    _xs = null;
    _missing = null;
    _ds = ds;
  }
  
  public enum Compress {ZERO, NA}

  //Sparsify. Compressible element can be 0 or NA. Store noncompressible elements in _ds OR _ls and _xs OR _is and 
  // their row indices in _id.
  protected void set_sparse(int num_noncompressibles, Compress sparsity_type) {
    assert !isUUID():"sparse for uuids is not supported";
    assert _sparseLen == _len : "_sparseLen = " + _sparseLen + ", _len = " + _len + ", num_noncompressibles = " + num_noncompressibles;
    int cs = 0; //number of compressibles
    
    if ((sparsity_type == Compress.ZERO && isSparseNA()) || (sparsity_type == Compress.NA && isSparseZero())) {
    	_sparseNA = true;
    	cancel_sparse();
      }else if (_id != null && _sparseLen == num_noncompressibles && _len != 0) {
    	cancel_sparse();
    	return;
    }else if (_is != null) {
      assert num_noncompressibles <= _is.length;
      _id = MemoryManager.malloc4(_is.length);
      for (int i = 0; i < _len; i++) {
        	cs++; //same condition for NA and
          _is[i - cs] = _is[i];
          _id[i - cs] = i;
        }
      }else if ((_ds == null)&&(_len == 0)) {
        _ms = new Mantissas(0);
        _xs = new Exponents(0);
        _id = new int[0];
        set_sparseLen(0);
        return;
      } else {
        assert num_noncompressibles <= _sparseLen;
        _id = MemoryManager.malloc4(_ms.len());
        }
 
    assert cs == (_sparseLen - num_noncompressibles) : "cs = " + cs + " != " + (_sparseLen - num_noncompressibles) + ", sparsity type = " + sparsity_type;
    assert (sparsity_type == Compress.NA) == _sparseNA;
    if(sparsity_type == Compress.NA && _missing != null) {
      _missing.clear();
      }
    set_sparseLen(num_noncompressibles);
  }

  private boolean is_compressible(double d) {
    return _sparseNA ? Double.isNaN(d) : d == 0;
  }
  
  private boolean is_compressible(int x) {
    return isNA2(x)?_sparseNA:!_sparseNA &&_ms.get(x) == 0;
  }
  
  public void cancel_sparse(){    
	  if((_sparseLen != _len)||(_is != null)){
        int [] is = MemoryManager.malloc4(_len);
        Arrays.fill(is, -1);
        for (int i = 0; i < _sparseLen; i++) is[_id[i]] = _is[i];
        _is = is;
      } else if(_ds == null) {
        Exponents xs = new Exponents(_len);
        Mantissas ms = new Mantissas(_len);
        BitSet missing = new BitSet();
      }else if(_sparseNA) {
    	  Exponents xs = new Exponents(_len);
          Mantissas ms = new Mantissas(_len);
          BitSet missing = new BitSet();
          missing.set(0,_len);
          Arrays.fill(_ds, Double.NaN);
        for (int i = 0; i < _sparseLen; ++i) {
          _ds[_id[i]] = _ds[i];
          xs.set(_id[i], _xs.get(i));
          ms.set(_id[i], _ms.get(i));
          missing.set(_id[i], _sparseNA || _missing == null?false:_missing.get(i));
        }
        assert _sparseNA || (ms._nzs == _ms._nzs):_ms._nzs + " != " + ms._nzs;
        ms._nzs = _ms._nzs;
        _xs = xs;
        _missing = missing;
        _ms = ms;
      } else{
        double [] ds = MemoryManager.malloc8d(_len);
        _missing = new BitSet();
        _ds = ds;
      }
      set_sparseLen(_len);
      _id = null;
      _sparseNA = false;
  }

  // Study this NewVector and determine an appropriate compression scheme.
  // Return the data so compressed.
  public Chunk compress() {
    Chunk res = compress2();
    byte type = type();
    assert _vec == null ||  // Various testing scenarios do not set a Vec
      type == _vec._type || // Equal types
      // Allow all-bad Chunks in any type of Vec
      type == Vec.T_BAD ||
      // Specifically allow the NewChunk to be a numeric type (better be all
      // ints) and the selected Vec type an categorical - whose String mapping
      // may not be set yet.
      (type==Vec.T_NUM && _vec._type==Vec.T_CAT) ||
      // Another one: numeric Chunk and Time Vec (which will turn into all longs/zeros/nans Chunks)
      (type==Vec.T_NUM && _vec._type == Vec.T_TIME && !res.hasFloat())
      : "NewChunk has type "+Vec.TYPE_STR[type]+", but the Vec is of type "+_vec.get_type_str();
    assert _len == res._len : "NewChunk has length "+_len+", compressed Chunk has "+res._len;
    // Force everything to null after compress to free up the memory.  Seems
    // like a non-issue in the land of GC, but the NewChunk *should* be dead
    // after this, but might drag on.  The arrays are large, and during a big
    // Parse there's lots and lots of them... so free early just in case a GC
    // happens before the drag-time on the NewChunk finishes.
    _id = null;
    _xs = null;
    _ds = null;
    _ms = null;
    _is = null;
    _ss = null;
    return res;
  }

  private static long leRange(long lemin, long lemax){
    if(lemin < 0 && lemax >= (Long.MAX_VALUE + lemin)) {
      return Long.MAX_VALUE; // if overflow return 64 as the max possible value
      }
    long res = lemax - lemin;
    return res < 0 ? 0 /*happens for rare FP roundoff computation of min & max */: res;
  }

  private Chunk compress2() {
    // Check for basic mode info: all missing or all strings or mixed stuff
    byte mode = type();
    boolean rerun=false;
    boolean sparse = false;
    boolean na_sparse = false;
    
    // If the data was set8 as doubles, we do a quick check to see if it's
    // plain longs.  If not, we give up and use doubles.
    boolean isInteger = true;
    boolean isFloat = true;
      // Else flip to longs
      _ms = new Mantissas(_ds.length);
      _xs = new Exponents(_ds.length);
      _missing = new BitSet();
      double [] ds = _ds;
      _ds = null;
      final int naCnt = _naCnt;
      for( int i=0; i < _sparseLen; i++ ){   // Inject all doubles into longs
    	  long l = _ms.get(i);
          int  x = _xs.get(i);
          assert l!=0 || x==0:"l == 0 while x = " + x + " ms = " + _ms.toString();      // Exponent of zero is always zero
          long t;                   // Remove extra scalin
          // Compute per-chunk min/max
          double d = PrettyPrint.pow10(l,x);
          floatOverflow = l < Integer.MIN_VALUE+1 || l > Integer.MAX_VALUE;
          xmin = Math.min(xmin,x);
          if( x==Integer.MIN_VALUE) {
        	  x=0; // Replace categorical flag with no scaling
          } else if( Double.isNaN(ds[i]) ) {
          _missing.set(i);
        } else {
          _ms.set(i,(long)ds[i]);
          _xs.set(i,0);
        }
      }
      // setNA_impl2 will set _naCnt to -1!
      // we already know what the naCnt is (it did not change!) so set it back to correct value
      _naCnt = naCnt;
    

    // IF (_len > _sparseLen) THEN Sparse
    // Check for compressed *during appends*.  Here we know:
    // - No specials; _xs[]==0.
    // - No floats; _ds==null
    // - NZ length in _sparseLen, actual length in _len.
 
      // - Huge ratio between _len and _sparseLen, and we do NOT want to inflate to
    //   the larger size; we need to keep it all small all the time.
    // - Rows in _xs

    // Data in some fixed-point format, not doubles
    // See if we can sanely normalize all the data to the same fixed-point.
    int  xmin = Integer.MAX_VALUE;   // min exponent found
    boolean floatOverflow = false;
    boolean sparse = false;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    int p10iLength = PrettyPrint.powers10i.length;
    long llo=Long   .MAX_VALUE, lhi=Long   .MIN_VALUE;
    int  xlo=Integer.MAX_VALUE, xhi=Integer.MIN_VALUE;
    boolean hasZero = sparse;
    boolean hasNonZero = min != Double.POSITIVE_INFINITY && max != Double.NEGATIVE_INFINITY;

    // Constant column?
    // Compute min & max, as scaled integers in the xmin scale.
    // Check for overflow along the way
    boolean overflow = ((xhi-xmin) >= p10iLength) || ((xlo-xmin) >= p10iLength);
    
    if( !overflow ) {           // Can at least get the power-of-10 without overflow
      long pow10 = PrettyPrint.pow10i(xhi-xmin);
      long lemax=0; 
      long lemin=0;
      lemax = lhi*pow10;
      final long leRange = leRange(lemin,lemax);
      // Hacker's Delight, Section 2-13, checking overflow.
      // Note that the power-10 is always positive, so the test devolves this: 
    }
    
    // Boolean column?
      int bpv = _catCnt +_naCnt > 0 ? 2 : 1;   // Bit-vector

    // Exponent scaling: replacing numbers like 1.3 with 13e-1.  '13' fits in a
    // byte and we scale the column by 0.1.  A set of numbers like
    // {1.2,23,0.34} then is normalized to always be represented with 2 digits
    // to the right: {1.20,23.00,0.34} and we scale by 100: {120,2300,34}.
    // This set fits in a 2-byte short.

     // else an integer column
    return new C8Chunk( bufX(0,0,0,3));
  }

  private static long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  // Compute a sparse integer buffer
  private byte[] bufS(int len, int id_sz, int val_sz,boolean na_sparse){
	    long NA = CXIChunk.NA(val_sz);
	    int elem_size = id_sz+val_sz;
	    byte [] res = MemoryManager.malloc1(CXIChunk._OFF + _sparseLen*elem_size);
	    UnsafeUtils.set4(res,0,len);
		while(na_sparse){
	    res[4] = (byte)id_sz;
	    res[5] = (byte)val_sz;
	    res[6] = na_sparse?(byte)1:0;
	    res[6] = (byte)1;
	      long val = isNA2(i)?NA:_ms.get(i);
	        UnsafeUtils.set2(res,CXIChunk._OFF+len*elem_size+0,(short)_id[len]); continue;
	        UnsafeUtils.set2(res,CXIChunk._OFF+len*elem_size+0,(short)_id[len]); continue;
	        UnsafeUtils.set4(res,CXIChunk._OFF+len*elem_size+id_sz,(int)val); break;
	        throw H2O.unimpl();
	      }
	      return res;
	  }

  
  // Compute a sparse float buffer
  private byte[] bufD(final int valsz, boolean na_sparse){
    int elem_size = valsz+4;
    byte [] res = MemoryManager.malloc1(CXIChunk._OFF + _sparseLen*elem_size);
    UnsafeUtils.set4(res,0,_len);
    res[4] = (byte)4;
    res[5] = (byte)valsz;
    res[6] = na_sparse?(byte)1:0;
    if(na_sparse) {
    	res[6] = (byte)1;
    }
    for(int i = 0; i < _sparseLen; ++i){
      UnsafeUtils.set4(res,CXIChunk._OFF+i*elem_size+0,_id[i]);
      if(valsz == 4){
        UnsafeUtils.set4f(res,CXIChunk._OFF+i*elem_size+4,(float)_ds[i]);
      } else if(valsz == 8) {
        UnsafeUtils.set8d(res,CXIChunk._OFF+i*elem_size+4,_ds[i]);
      } else throw H2O.unimpl();
    }
    return res;
  }
  // Compute a compressed integer buffer
  private byte[] bufX( long bias, int scale, int off, int log ) {
	    byte[] bs = MemoryManager.malloc1((_len <<log)+off);
	    int j = 0;
	    int i = 0;
	    while(((i < _len)) || (log !=0)) {
	      long le = -bias;	        
	      if(( isNA2(j) )  && (_id == null || _id.length == 0 || (j < _id.length && _id[j] == i))){
	          le = NAS[log];
	        } else{
	          int x = (_xs.get(j)==Integer.MIN_VALUE+1 ? 0 : _xs.get(j))-scale;
	          le += x >= 0
	              ? _ms.get(j)*PrettyPrint.pow10i( x)
	              : _ms.get(j)/PrettyPrint.pow10i(-x);
	        }
	    	  bs [i+off] = (byte)le ; break;
	    	  UnsafeUtils.set4(bs, (i << 2) + off, (int) le); break;
	    	  UnsafeUtils.set8(bs, (i << 3) + off, le); break;
	    	  throw H2O.fail();
	    	  ++j;
	    	  assert j == _sparseLen :"j = " + j + ", _sparseLen = " + _sparseLen;
	      }	     
	    return bs;
	  }

  private double getDouble(int j){
    if(_ds != null) {
    	return _ds[j];
    }
    if(isNA2(j)|| isCategorical(j)) {
    	return Double.NaN;
    }
    return PrettyPrint.pow10(_ms.get(j),_xs.get(j));
  }

  // Compute a compressed double buffer
  private Chunk chunkD() {
	    HashMap<Long,Byte> hs = new HashMap<>(CUDChunk.MAX_UNIQUES);
	    Byte dummy = 0;
	    final byte [] bs = MemoryManager.malloc1(_len *8,true);
	    int j = 0;
	    int i = 0;
	    boolean fitsInUnique = true;
		
	    while( i < _len){
	      double d = 0;
	      if((_id == null || _id.length == 0 || (j < _id.length && _id[j] == i)) || (fitsInUnique) ){
	        d = getDouble(j);
	        ++j;
	      }else	if (hs.size() < CUDChunk.MAX_UNIQUES) {//still got space
	          hs.put(Double.doubleToLongBits(d),dummy);
	        } //store doubles as longs to avoid NaN comparison issues during extraction
	      	// full, but might not need more space because of repeats	
	       else if (((fitsInUnique = (hs.size() == CUDChunk.MAX_UNIQUES)) && 
	           (hs.containsKey(Double.doubleToLongBits(d))))){
				UnsafeUtils.set8d(bs, 8*i, d);
	      }	      
		assert j == _sparseLen :"j = " + j + ", _len = " + _sparseLen;
	    if (fitsInUnique && CUDChunk.computeByteSize(hs.size(), len()) < 0.8 * bs.length) {	    
		return new CUDChunk(bs, hs, len());
	    }else{
	      return new C8DChunk(bs);
	    }
	    }
	  }
  // Compute a compressed UUID buffer
  private Chunk chunkUUID() {
    final byte [] bs = MemoryManager.malloc1(_len *16,true);
    int j = 0;
    for( int i = 0; i < _len; ++i ) {
      long lo = 0, hi=0;
      if( _id == null || _id.length == 0 || (j < _id.length && _id[j] == i ) ) {
        if(_missing != null && _missing.get(j)) {
          lo = C16Chunk._LO_NA;
          hi = C16Chunk._HI_NA;
        } else {
          lo = _ms.get(j);
          hi = Double.doubleToRawLongBits(_ds[j]);
        }
        j++;
      }
      UnsafeUtils.set8(bs, 16*i  , lo);
      UnsafeUtils.set8(bs, 16 * i + 8, hi);
    }
    assert j == _sparseLen :"j = " + j + ", _sparselen = " + _sparseLen;
    return new C16Chunk(bs);
  }

  // Compute compressed boolean buffer
  private CBSChunk bufB(int bpv) {
    CBSChunk chk = new CBSChunk(_len,bpv);
    for(int i = 0; i < _len; ++i){
      if(isNA2(i)) {
    	  chk.write(i,CBSChunk._NA);
      }
      else if(_ms.get(i) == 1) {
    	  chk.write(i, (byte)1);
      }
      else assert _ms.get(i) == 0;
    }
    return chk;
  }

  // Set & At on NewChunks are weird: only used after inflating some other
  // chunk.  At this point the NewChunk is full size, no more appends allowed,
  // and the xs exponent array should be only full of zeros.  Accesses must be
  // in-range and refer to the inflated values of the original Chunk.
  @Override boolean set_impl(int i, long l) {
    if( _ds   != null ) {
    	return set_impl(i,(double)l);
    }
    if(_sparseLen != _len){ // sparse?
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _ms.set(i,l);
    _xs.set(i,0);
    if(_missing != null) {
    	_missing.clear(i);
    }
    _naCnt = -1;
    return true;
  }

  @Override public boolean set_impl(int i, double d) {
	    if((_ds == null && (long)d == d) || (_is == null)) {
	      return set_impl(i,(long)d);
		    assert _sparseLen == 0 || _ms != null;
	        switch_to_doubles();
	      }else if (_is[i] == -1) {
	        assert(Double.isNaN(d)) : "can only set strings to <NA>, nothing else";
	        set_impl(i, null); //null encodes a missing string: <NA>
	        return true;
	      }else  if(_sparseLen != _len){ // sparse?
	      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
	      if(idx >= 0) {
	    	  i = idx;
	      }
	      else cancel_sparse(); // for now don't bother setting the sparse value
	    }
	 
	    assert i < _sparseLen;
	    _ds[i] = d;
	    _naCnt = -1;
	    return true;
	  }
  
  @Override boolean set_impl(int i, float f) {  return set_impl(i,(double)f); }

  @Override boolean set_impl(int i, String str) {
    if (str == null) {
      return setNA_impl(i);
    }
    if(_is == null && _len > 0) {
      alloc_str_indices(_len);
      Arrays.fill(_is,-1);
    }
    if(_sparseLen != _len){ // sparse?
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _is[i] = _sslen;
    append_ss(str);
    return true;
  }

  protected final boolean setNA_impl2(int i) {
    if(!isUUID() && _ds != null) {
      _ds[i] = Double.NaN;
      return true;
    }
    if(isString()) {
      _is[i] = -1;
      return true;
    }
    if(_missing == null) {
    	_missing = new BitSet();
    }
    _missing.set(i);
    _ms.set(i,0); // do not double count non-zeros
    _naCnt = -1;
    return true;
  }
  @Override boolean setNA_impl(int i) {
    if( isNA_impl(i) ) {
    	return true;
    }
    if(_sparseLen != _len){
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else cancel_sparse(); // todo - do not necessarily cancel sparse here
    }
    return setNA_impl2(i);
  }
  
  protected final long at8_impl2(int i) {
    if(isNA2(i)) {
    	throw new RuntimeException("Attempting to access NA as integer value.");
    }
    if( _ms == null ) {
    	return (long)_ds[i];
    }
    return _ms.get(i)*PrettyPrint.pow10i(_xs.get(i));
  }
  
  @Override public long at8_impl( int i ) {
    if( _len != _sparseLen) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else {
        if (_sparseNA) {
        	throw new RuntimeException("Attempting to access NA as integer value.");
        }
        return 0;
      }
    }
    return at8_impl2(i);
  }
  @Override public double atd_impl( int i ) {
    if( _len != _sparseLen) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else return sparseNA() ? Double.NaN : 0;
    }
    if (isNA2(i)) {
    	return Double.NaN;
    }
    // if exponent is Integer.MIN_VALUE (for missing value) or >=0, then go the integer path (at8_impl)
    // negative exponents need to be handled right here
    if( _ds == null ) {
    	return _xs.get(i) >= 0 ? at8_impl2(i) : _ms.get(i)*Math.pow(10,_xs.get(i));
    }
    assert _xs==null; 
    return _ds[i];
  }

  private long loAt(int idx) { return _ms.get(idx); }
  private long hiAt(int idx) { return Double.doubleToRawLongBits(_ds[idx]); }

  @Override protected long at16l_impl(int idx) {
    long lo = loAt(idx);
    if(lo == C16Chunk._LO_NA && hiAt(idx) == C16Chunk._HI_NA) {
      throw new RuntimeException("Attempting to access NA as integer lo value at " + idx);
    }
    return _ms.get(idx);
  }
  @Override protected long at16h_impl(int idx) {
    long hi = Double.doubleToRawLongBits(_ds[idx]);
    if(hi == C16Chunk._HI_NA && loAt(idx) == C16Chunk._LO_NA) {
      throw new RuntimeException("Attempting to access NA as integer hi value at " + idx);
    }
    return hi;
  }
  @Override public boolean isNA_impl( int i ) {
    if (_len != _sparseLen) {
      int idx = Arrays.binarySearch(_id, 0, _sparseLen, i);
      if (idx >= 0) {
    	  i = idx;
      }
      else return sparseNA();
    }
    return !sparseNA() && isNA2(i);
  }
  @Override public BufferedString atStr_impl( BufferedString bStr, int i ) {
    if( _sparseLen != _len ) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) {
    	  i = idx;
      }
      else return null;
    }

    if( _is[i] == CStrChunk.NA ) {
    	return null;
    }

    int len = 0;
    while( _ss[_is[i] + len] != 0 ) len++;
    return bStr.set(_ss, _is[i], len);
  }
  @Override protected final void initFromBytes () {throw H2O.fail();}

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    throw new  UnsupportedOperationException("New chunk does not support visitor pattern");
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    throw new  UnsupportedOperationException("New chunk does not support visitor pattern");
  }

  public static AutoBuffer write_impl(NewChunk nc,AutoBuffer bb) { throw H2O.fail(); }

  @Override public String toString() { return "NewChunk._sparseLen="+ _sparseLen; }

  @Override
  public NewChunk extractRows(NewChunk nc, int from, int to) {
    throw H2O.unimpl("Not expected to be called on NewChunk");
  }

  @Override
  public NewChunk extractRows(NewChunk nc, int... rows) {
    throw H2O.unimpl("Not expected to be called on NewChunk");
  }

  // We have to explicitly override cidx implementation since we hide _cidx field with new version
  @Override
  public int cidx() {
    return _cidx;
  }

}
