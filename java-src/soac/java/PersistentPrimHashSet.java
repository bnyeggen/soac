package soac.java;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import clojure.lang.ASeq;
import clojure.lang.ChunkBuffer;
import clojure.lang.IChunk;
import clojure.lang.IChunkedSeq;
import clojure.lang.IHashEq;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.Obj;
import clojure.lang.PersistentList;
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;

@SuppressWarnings("rawtypes")
public class PersistentPrimHashSet 
	extends PersistentPrimHashTable 
	implements IObj, Collection, Set, IPersistentSet, IHashEq {
	
	PersistentPrimHashSet(IPersistentVector data, IPersistentMap meta, int size, Object free) {
		super(data, meta, size, free);
	}
	
	public static PersistentPrimHashSet fromProto(IPersistentVector data, Object free){
		IPersistentVector dataStore = (IPersistentVector)data.empty();
		for(int i=0; i<neighborhood; i++) dataStore = dataStore.cons(free);
		return new PersistentPrimHashSet(dataStore, null, 0, free);
	}
	
	public static PersistentPrimHashSet fromProto(IPersistentVector data, Object free, int size){
		IPersistentVector dataStore = (IPersistentVector)data.empty();
		for(int i=0; i<Math.max(neighborhood, (Integer.highestOneBit(size)<<1)); i++){
			dataStore = dataStore.cons(free);
		}
		return new PersistentPrimHashSet(dataStore, null, 0, free);
	}
	
	
	public PersistentPrimHashSet rehash(){
		return rehash(1);
	}
	
	//TODO: Specialize this for when _ks is an IEditableCollection
	public PersistentPrimHashSet rehash(int increment){
		int newCapacity = increment>0 ? _capacity << increment : _capacity >> -increment;
		IPersistentVector newData = isEmpty() ? _ks : (IPersistentVector)_ks.empty();
		while(newData.count() < newCapacity) newData = newData.cons(_free);
		
		PersistentPrimHashSet out = new PersistentPrimHashSet(newData, _meta, 0, _free);
		//Avoid traversing to check for free when we have no data
		if(size()==0) return out;
		for(Object o : this){
			out = (PersistentPrimHashSet)out.cons(o);
		}
		return out;
	}
	
	@Override
	public IPersistentCollection cons(Object o) {
		if(Util.equiv(_free,o)) throw new RuntimeException("Cannot sensibly conj free value");
		if(load() > rehashThresholdHi) return rehash().cons(o);

		//Find the first free position, or the element if it exists inside the neighborhood
		//Could make this slightly faster by scanning thru backing array directly instead
		//of using nth
		int pos = bitMod(o.hashCode()); int ct = 0; int freePos = -1;
		for(;;){
			final Object k = _ks.nth(pos);
			if(Util.equiv(k, o)) return this;
			if(k.equals(_free)){
				//Element may be beyond the free position, but still in the neighborhood
				if(ct<neighborhood && freePos == -1) freePos = pos;
				//If we're outside the neighborhood though, the element is guaranteed not to be there.
				else if(ct >= neighborhood) break;
			}
			pos = wrappingInc(pos);
			ct++;
		}

		//freePos now represents the first free slot, and ct how far away it is from the insert point
		if(ct<neighborhood) {
			//freePos will be set to a valid position IFF there is a free position in the neighborhood
			//and the element is not in the neighborhood already
			return new PersistentPrimHashSet(_ks.assocN(freePos, o), _meta, _size+1, _free);
		} 
		//Shift elements, recursively if necessary
		else {
			//Temporarily store o in its invalid pos, while we search for a valid exchange
			IPersistentVector newData = _ks.assocN(pos, o);
			int topPos = pos;
			int bottomPos = firstShiftablePos(pos);
			ct = 0;
			while(ct < neighborhood){
				//Can the bottom element be shifted to the top?
				if(checkPosition(newData.nth(bottomPos), topPos)){
					//And the top element switched to the bottom?
					if(checkPosition(o, bottomPos)){
						//Cool, make the switch and return
						return new PersistentPrimHashSet(exchange(newData, bottomPos, topPos), _meta, _size+1, _free);
					}
					//Swap them anyway, and check further down for a valid swap, checking the new positions. 
					else {
						newData = exchange(newData, bottomPos, topPos);
						topPos = bottomPos;
						bottomPos = firstShiftablePos(bottomPos);
						ct = 0;
					}
				} else {
					bottomPos = wrappingInc(bottomPos);
					ct++;
				}
			}
			return rehash().cons(o);
		}
	}
	@Override
	public boolean contains(Object o) {
		return findIndex(o) >= 0;
	}
	@Override
	public boolean containsAll(Collection c) {
		if(c.size()>size()) return false;
		for(Object o: c){
			if(!contains(o)) return false;
		}
		return true;
	}
	@Override
	public IPersistentSet disjoin(Object o) {
		if(load() < rehashThresholdLo && _capacity > neighborhood) 
			return rehash(-1).disjoin(o);
		int pos = findIndex(o);
		if(pos<0) return this;
		return new PersistentPrimHashSet(_ks.assocN(pos, _free), _meta, _size - 1, _free);
	}

	@Override
	public boolean equiv(Object o) {
		if(! (o instanceof Set)) return false;
		final Set oset = (Set)o;
		if(oset.size() != size()) return false;
		for(Object i : oset) if(!contains(i)) return false;
		return true;
	}
	@Override
	public Object get(Object o) {
		int pos = findIndex(o);
		if(pos<0) return null;
		return o;
	}
	
	private class FilteredSeq extends ASeq implements IChunkedSeq{
		private static final long serialVersionUID = 1L;

		final IPersistentMap meta;
		final IChunk fChunk;
		final IChunkedSeq rest;
		final int offset;
		protected FilteredSeq(IPersistentMap meta, IChunk fChunk, IChunkedSeq rest, int offset) {
			this.meta = meta;
			this.fChunk = fChunk;
			this.rest = rest;
			this.offset = offset;
		}
		public FilteredSeq(IChunkedSeq s, IPersistentMap meta) {
			this.meta = meta;
			offset = 0;
			IChunkedSeq thisRest = (IChunkedSeq) s;
			IChunk thisFirst = s.chunkedFirst();
			final ChunkBuffer buf = new ChunkBuffer(thisFirst.count());
			boolean appended = false;
			while (!appended) {
				for (int i = 0; i < thisFirst.count(); i++) {
					if (!thisFirst.nth(i).equals(_free)) {
						appended = true;
						buf.add(thisFirst.nth(i));
					}
				}
				thisRest = (IChunkedSeq) thisRest.chunkedNext();
				if (thisRest == null)
					break;
				thisFirst = thisRest.chunkedFirst();
			}
			fChunk = buf.chunk();
			rest = thisRest;
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
			if (this.rest == null)
				return null;
			IChunkedSeq thisRest = rest;
			IChunk thisFirst = thisRest.chunkedFirst();
			final ChunkBuffer buf = new ChunkBuffer(thisFirst.count());
			boolean appended = false;
			while (!appended) {
				for (int i = 0; i < thisFirst.count(); i++) {
					if (!thisFirst.nth(i).equals(_free)) {
						appended = true;
						buf.add(thisFirst.nth(i));
					}
				}
				thisRest = (IChunkedSeq) thisRest.chunkedNext();
				if (thisRest == null)
					break;
				thisFirst = thisRest.chunkedFirst();
			}
			if (buf.count() == 0)
				return null;
			return new FilteredSeq(meta, buf.chunk(), thisRest, 0);
		}
		@Override
		public Object first() {
			return fChunk.nth(offset);
		}
		@Override
		public ISeq next() {
			if (offset + 1 < fChunk.count())
				return new FilteredSeq(meta, fChunk, rest, offset + 1);
			return chunkedNext();
		}
		@Override
		public Obj withMeta(IPersistentMap meta) {
			return new FilteredSeq(this, meta);
		}
	}
		
	@Override
	public ISeq seq() {
		if(_size==0) return null;
		return new FilteredSeq((IChunkedSeq)_ks.seq(), null);
	}
	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentPrimHashSet(_ks, meta, _size, _free);
	}
	@Override
	public PersistentPrimHashSet empty() {
		return fromProto(_ks, _free);
	}	@Override
	public int hashCode() {
		int sum = 0;
		for(Object o: this) sum+= Util.hash(o);
		return sum;
	}
	@Override
	public int hasheq() {
		int sum = 0;
		for(Object o: this) sum+= Util.hasheq(o);
		return sum;
	}
	@Override
	public boolean isEmpty() {
		return _size==0;
	}
	@Override
	public Iterator iterator() {
		return new SeqIterator(seq());
	}
	@Override
	public int size() { return _size; }
	@Override
	public Object invoke(Object arg1) {
		return get(arg1);
	}
		
	@Override
	public Object[] toArray() { return RT.seqToArray(seq()); }
	@Override
	public Object[] toArray(Object[] a) { return RT.seqToPassedArray(seq(), a); }
	@Override
	public boolean add(Object e) { throw new UnsupportedOperationException(); }
	@Override
	public boolean addAll(Collection c) { throw new UnsupportedOperationException(); }
	@Override
	public void clear() { throw new UnsupportedOperationException(); }
	@Override
	public boolean remove(Object o) { throw new UnsupportedOperationException(); }
	@Override
	public boolean removeAll(Collection c) { throw new UnsupportedOperationException(); }
	@Override
	public boolean retainAll(Collection c) { throw new UnsupportedOperationException(); }
}
