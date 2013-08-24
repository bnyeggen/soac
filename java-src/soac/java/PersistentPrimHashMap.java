package soac.java;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import clojure.lang.APersistentMap;
import clojure.lang.ASeq;
import clojure.lang.ChunkBuffer;
import clojure.lang.IChunk;
import clojure.lang.IChunkedSeq;
import clojure.lang.IHashEq;
import clojure.lang.IMapEntry;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.MapEntry;
import clojure.lang.MapEquivalence;
import clojure.lang.Obj;
import clojure.lang.PersistentHashSet;
import clojure.lang.PersistentList;
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;

@SuppressWarnings("rawtypes")
public class PersistentPrimHashMap extends PersistentPrimHashTable implements Map, IObj, IPersistentMap, Iterable, IHashEq, MapEquivalence{
	final IPersistentVector _vs;

	private PersistentPrimHashMap(IPersistentVector ks, IPersistentVector vs, IPersistentMap meta, int size, Object free) {
		super(ks, meta, size, free);
		this._vs = vs;
	}
	
	public static PersistentPrimHashMap fromProto(IPersistentVector ks, IPersistentVector vs, Object free){
		IPersistentVector newKs = (IPersistentVector)ks.empty();
		IPersistentVector newVs = (IPersistentVector)vs.empty();
		for(int i = 0; i<neighborhood; i++){
			newKs = newKs.cons(free);
			//Everything can store some version of 0
			newVs = newVs.cons(0);
		}
		return new PersistentPrimHashMap(newKs, newVs, null, 0, free);
	}
	
	public static PersistentPrimHashMap fromProto(IPersistentVector ks, IPersistentVector vs, Object free, int size){
		IPersistentVector newKs = (IPersistentVector)ks.empty();
		IPersistentVector newVs = (IPersistentVector)vs.empty();
		
		for(int i=0; i<Math.max(neighborhood, (Integer.highestOneBit(size)<<1)); i++){
			newKs = newKs.cons(free);
			newVs = newVs.cons(free);
		}
		return new PersistentPrimHashMap(newKs, newVs, null, 0, free);
	}	
	
	public IPersistentVector getRawVals(){
		return _vs;
	}
	
	public PersistentPrimHashMap rehash(){
		return rehash(1);
	}
	
	public PersistentPrimHashMap rehash(int increment){
		int newCapacity = increment>0 ? _capacity << increment : _capacity >> -increment;
		IPersistentVector newKs = (IPersistentVector)_ks.empty();
		IPersistentVector newVs = (IPersistentVector)_vs.empty();
		for(int i=0; i<newCapacity; i++) {
			newKs = newKs.cons(_free);
			newVs = newVs.cons(0);
		}
		
		PersistentPrimHashMap out = new PersistentPrimHashMap(newKs, newVs, _meta, 0, _free);
		for(ISeq es = seq(); es != null; es = es.next()){
			final Map.Entry e = (Map.Entry)es.first();
			out = (PersistentPrimHashMap)out.assoc(e.getKey(), e.getValue());
		}
		return out;
	}
	
	@Override
	public boolean containsKey(Object key) {
		return findIndex(key)>=0;
	}	
	@Override
	public IPersistentMap assoc(Object k, Object v) {
		if(Util.equiv(_free,k)) throw new RuntimeException("Cannot sensibly have free value as a key");
		if(load() > rehashThresholdHi) return rehash().assoc(k,v);

		//Find the first free position, or the element if it exists
		int pos = bitMod(k.hashCode()); int ct = 0;
		for(;;){
			final Object thisK = _ks.nth(pos);
			if(Util.equiv(k, thisK)) {
				return new PersistentPrimHashMap(_ks, _vs.assocN(pos, v), _meta, _size, _free);
			}
			if(thisK.equals(_free)) break;
			pos = wrappingInc(pos);
			ct++;
		}
		//pos now represents the first free slot, and ct how far away it is from the insert point
		if(ct<neighborhood) {
			return new PersistentPrimHashMap(_ks.assocN(pos, k), _vs.assocN(pos, v), _meta, _size+1, _free);
		} 
		//Shift elements, recursively if necessary
		else {
			//Temporarily store o in its invalid pos, while we search for a valid exchange
			IPersistentVector newKs = _ks.assocN(pos, k);
			IPersistentVector newVs = _vs.assocN(pos, v);
			int topPos = pos;
			int bottomPos = firstShiftablePos(pos);
			ct = 0;
			while(ct < neighborhood){
				//Can the bottom element be shifted to the top?
				if(checkPosition(_ks.nth(bottomPos), topPos)){
					//And the top element switched to the bottom?
					if(checkPosition(k, bottomPos)){
						//Cool, make the switch and return
						return new PersistentPrimHashMap(exchange(_ks, bottomPos, topPos), exchange(_vs, bottomPos, topPos)
								,_meta, _size+1, _free);
					}
					//Swap them anyway, and check further down for a valid swap, checking the new positions. 
					else {
						newKs = exchange(newKs, bottomPos, topPos);
						newVs = exchange(newVs, bottomPos, topPos);
						topPos = bottomPos;
						bottomPos = firstShiftablePos(bottomPos);
						ct = 0;
					}
				} else {
					bottomPos = wrappingInc(bottomPos);
					ct++;
				}
			}
			return rehash().assoc(k,v);
		}
	}
	@Override
	public IPersistentMap assocEx(Object k, Object v) {
		if(containsKey(k)) throw new RuntimeException();
		return assoc(k,v);
	}
	@Override
	public IPersistentCollection cons(Object o) {
		if(o instanceof Map.Entry){
			final Map.Entry v = (Map.Entry)o;
			return assoc(v.getKey(), v.getValue());
		} else if (o instanceof IPersistentVector){
			final IPersistentVector v = (IPersistentVector)o;
			if(v.count()!=2) throw new RuntimeException("Must have 2 elements");
			return assoc(v.nth(0), v.nth(1));
		}
		//Assume a seq of Map.Entrys
		IPersistentMap out = this;
		for(ISeq es = RT.seq(o); es != null; es = es.next()){
			Map.Entry e = (Map.Entry) es.first();
			out = out.assoc(e.getKey(), e.getValue());
		}
		return out;
	}
	@Override
	public boolean containsValue(Object value) {
		for(int i=0; i<_capacity; i++){
			if(value.equals(_vs.nth(i)) && !_ks.nth(i).equals(_free)) return true;
		}
		return false;
	}
	@Override
	public IPersistentCollection empty() {
		return fromProto(_ks, _vs, _free);
	}
	@Override
	public IMapEntry entryAt(Object o) {
		final int pos = findIndex(o);
		if(pos>=0) return new MapEntry(_ks.nth(pos), _vs.nth(pos));
		return null;
	}
	@Override
	public Set entrySet() {
		PersistentHashSet out = PersistentHashSet.EMPTY;
		for(ISeq es = seq(); es != null; es = es.next()){
			out = (PersistentHashSet)out.cons(es.first());
		}
		return out;
	}
	@Override
	public boolean equiv(Object o) {
		if(! (o instanceof Map)) return false;
		if(o instanceof IPersistentMap && !(o instanceof MapEquivalence))
			return false;
		Map m = (Map)o;
		if(m.size() != size())
			return false;

		for(ISeq s = seq(); s != null; s = s.next())
			{
			Map.Entry e = (Map.Entry) s.first();
			boolean found = m.containsKey(e.getKey());

			if(!found || !Util.equiv(e.getValue(), m.get(e.getKey())))
				return false;
			}

		return true;

	}
	@Override
	public boolean equals(Object obj) {
		return APersistentMap.mapEquals(this, obj);
	}
	@Override
	public Object get(Object key) {
		return valAt(key);
	}
	@Override
	public int hashCode() {
		return APersistentMap.mapHash(this);
	}
	@Override
	public int hasheq() {
		return APersistentMap.mapHasheq(this);
	}
	
	private class FilteredSeq extends ASeq implements IChunkedSeq{
		private static final long serialVersionUID = 1L;

		final IPersistentMap meta;
		//Consists of MapEntrys
		final IChunk fChunk;

		final IChunkedSeq rest_ks;
		final IChunkedSeq rest_vs;

		final int offset;
		
		protected FilteredSeq(IPersistentMap meta, IChunk fChunk, 
							  IChunkedSeq rest_ks, IChunkedSeq rest_vs, int offset) {
			this.meta = meta;
			this.offset = offset;
			this.fChunk = fChunk;
			this.rest_ks = rest_ks;
			this.rest_vs = rest_vs;
		}
		public FilteredSeq(IChunkedSeq ks, IChunkedSeq vs, IPersistentMap meta) {
			this.meta = meta;
			offset = 0;
			IChunkedSeq thisRestKs = (IChunkedSeq) ks;
			IChunkedSeq thisRestVs = (IChunkedSeq) vs;
			//If these have different lengths, it gets complicated, but for Vectors and Vecs they
			//are the same
			IChunk thisFirstKs = ks.chunkedFirst();
			IChunk thisFirstVs = vs.chunkedFirst();
			
			if(thisFirstKs.count() != thisFirstVs.count())
				throw new RuntimeException("Different chunk sizes between keys and vals not implemented yet");
			final int chunkSize = thisFirstKs.count();
			
			final ChunkBuffer buf = new ChunkBuffer(chunkSize);
			boolean appended = false;
			while (!appended) {
				for (int i = 0; i < chunkSize; i++) {
					if (!thisFirstKs.nth(i).equals(_free)) {
						appended = true;
						buf.add(new MapEntry(thisFirstKs.nth(i), thisFirstVs.nth(i)));
					}
				}
				thisRestKs = (IChunkedSeq) thisRestKs.chunkedNext();
				thisRestVs = (IChunkedSeq) thisRestVs.chunkedNext();
				if (thisRestKs == null || thisRestVs == null)
					break;
				thisFirstKs = thisRestKs.chunkedFirst();
				thisFirstVs = thisRestVs.chunkedFirst();
			}
			fChunk = buf.chunk();
			this.rest_ks = thisRestKs;
			this.rest_vs = thisRestVs;
		}
		@Override
		public IChunk chunkedFirst() {
			return fChunk;
		}
		@Override
		public ISeq chunkedMore() {
			final ISeq out = chunkedNext();
			if (out == null)
				return PersistentList.EMPTY;
			return out;
		}
		@Override
		public ISeq chunkedNext() {
			if (this.rest_ks == null || this.rest_vs == null)
				return null;
			IChunkedSeq thisRestKs = rest_ks;
			IChunkedSeq thisRestVs = rest_vs;
			
			IChunk thisFirstKs = thisRestKs.chunkedFirst();
			IChunk thisFirstVs = thisRestVs.chunkedFirst();

			if(thisFirstKs.count() != thisFirstVs.count())
				throw new RuntimeException("Different chunk sizes between keys and vals not implemented yet");
			final int chunkSize = thisFirstKs.count();
			
			final ChunkBuffer buf = new ChunkBuffer(chunkSize);
			boolean appended = false;
			while (!appended) {
				for (int i = 0; i < chunkSize; i++) {
					if (!thisFirstKs.nth(i).equals(_free)) {
						appended = true;
						buf.add(new MapEntry(thisFirstKs.nth(i), thisFirstVs.nth(i)));
					}
				}
				thisRestKs = (IChunkedSeq) thisRestKs.chunkedNext();
				thisRestVs = (IChunkedSeq) thisRestVs.chunkedNext();
				if (thisRestKs == null || thisRestVs == null)
					break;
				thisFirstKs = thisRestKs.chunkedFirst();
				thisFirstVs = thisRestVs.chunkedFirst();
			}
			if (buf.count() == 0)
				return null;
			return new FilteredSeq(meta, buf.chunk(), thisRestKs, thisRestVs, 0);
		}
		@Override
		public Object first() {
			return fChunk.nth(offset);
		}
		@Override
		public ISeq next() {
			if (offset + 1 < fChunk.count())
				return new FilteredSeq(meta, fChunk, rest_ks, rest_vs, offset + 1);
			return chunkedNext();
		}
		@Override
		public Obj withMeta(IPersistentMap meta) {
			return new FilteredSeq(meta, fChunk, rest_ks, rest_vs, offset);
		}
	}

	
	@Override
	public ISeq seq() {
		if(_size==0) return null;
		return new FilteredSeq((IChunkedSeq)_ks.seq(), (IChunkedSeq)_vs.seq(), null);
	}
	@Override
	public Collection values() {
		IPersistentVector out = (IPersistentVector)_vs.empty();
		for(int i=0; i<_capacity; i++){
			if(!_ks.nth(i).equals(_free)) out = out.cons(_vs.nth(i));
		}
		//As a practical matter, both Vector & GVec conform to Collection
		return (Collection)out;
	}
	@Override
	public Object valAt(Object k) {
		final int pos = findIndex(k);
		if(pos>=0) return _vs.nth(pos);
		return null;
	}
	@Override
	public Object valAt(Object k, Object other) {
		final Object out = valAt(k);
		return (out==null) ? other : out;
	}
	@Override
	public IPersistentMap without(Object k) {
		if(load() < rehashThresholdLo && _capacity > neighborhood) 
			return rehash(-1).without(k);
		final int pos = findIndex(k);
		if(pos>=0) return new PersistentPrimHashMap(_ks.assocN(pos,_free), _vs, _meta, _size-1, _free);
		return this;
	}
	@Override
	public boolean isEmpty() {
		return _size==0;
	}
	@Override
	public Set keySet() {
		return new PersistentPrimHashSet(_ks, null, _size, _free);
	}
	@Override
	public Iterator iterator() {
		return new SeqIterator(seq());
	}
	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentPrimHashMap(_ks, _vs, meta, _size, _free);
	}
	@Override
	public int size() {
		return _size;
	}
	@Override
	public Object invoke(Object arg1) {
		return valAt(arg1);
	}
	@Override
	public Object invoke(Object arg1, Object arg2) {
		return valAt(arg1, arg2);
	}
	@Override
	public void clear() { throw new UnsupportedOperationException(); }
	@Override
	public Object put(Object key, Object value) {  throw new UnsupportedOperationException(); }
	@Override
	public void putAll(Map m) {  throw new UnsupportedOperationException(); }
	@Override
	public Object remove(Object key) {  throw new UnsupportedOperationException(); }

}
