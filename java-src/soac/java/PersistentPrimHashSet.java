package soac.java;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import clojure.lang.AFn;
import clojure.lang.ASeq;
import clojure.lang.IHashEq;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.Obj;
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;

@SuppressWarnings("rawtypes")
public class PersistentPrimHashSet extends AFn implements IObj, Collection, Set, IPersistentSet, IHashEq {
	final IPersistentVector _data;
	final IPersistentMap _meta;
	final int _size, _capacity;
	//Should not be null
	final Object _free;
	
	public static final int neighborhood = 32;
	public static final double rehashThresholdHi = 0.8;
	public static final double rehashThresholdLo = 0.25;
	
	PersistentPrimHashSet(IPersistentVector data, IPersistentMap meta, int size, Object free) {
		this._data = data;
		this._meta = meta;
		this._size = size;
		this._capacity = data.count();
		this._free = free;
	}
	
	public static PersistentPrimHashSet fromProto(IPersistentVector data, Object free){
		IPersistentVector dataStore = (IPersistentVector)data.empty();
		for(int i=0; i<neighborhood; i++) dataStore = dataStore.cons(free);
		return new PersistentPrimHashSet(dataStore, null, 0, free);
	}
	
	public int wrappingInc(int i){
		return (i+1) & (_capacity - 1);
	}
	
	public int bitMod(int i){
		return i & (_capacity - 1);
	}
	
	public int getCapacity(){
		return _capacity;
	}
	
	public Object getFree(){
		return _free;
	}
	
	public IPersistentVector getRawData(){
		return _data;
	}
	
	public int findIndex(Object o){
		int pos = bitMod(o.hashCode());
		for(int i=0; i<neighborhood; i++){
			if(_data.nth(pos).equals(o)) return pos;
			pos = wrappingInc(pos);
		}
		return -1;
	}
	
	public int firstShiftablePos(int i){
		return bitMod(i - neighborhood + 1);
	}
	
	public double load(){
		return ((double)_size) / ((double)_capacity);
	}
	
	//Return if the object would "fit" at that position
	public boolean checkPosition(Object o, int pos){
		if(o.equals(_free)) return true;
		final int bottom = bitMod(o.hashCode());
		final int top = bitMod(o.hashCode() + neighborhood);
		if(top > bottom) return (top > pos && pos >= bottom);
		else return (top > pos || pos >= bottom);
	}
	
	public static IPersistentVector exchange(IPersistentVector v, int i, int j){
		Object iObj = v.nth(i);
		return v.assocN(i, v.nth(j)).assocN(j, iObj);
	}
	
	public PersistentPrimHashSet rehash(){
		return rehash(1);
	}
	
	public PersistentPrimHashSet rehash(int increment){
		int newCapacity = _capacity << increment;
		IPersistentVector newData = (IPersistentVector)_data.empty();
		for(int i=0; i<newCapacity; i++) newData = newData.cons(_free);
		
		PersistentPrimHashSet out = new PersistentPrimHashSet(newData, _meta, 0, _free);
		for(Object o : this){
			out = (PersistentPrimHashSet)out.cons(o);
		}
		return out;
	}
	
	@Override
	public IPersistentCollection cons(Object o) {
		if(load() > rehashThresholdHi) return rehash().cons(o);

		int pos = bitMod(o.hashCode()); int ct = 0;
		//Find the first free position
		while(!_data.nth(pos).equals(_free)){
			pos = wrappingInc(pos);
			ct++;
		}
		//pos now represents the first free slot, and ct how far away it is from the insert point
		if(ct<neighborhood) {
			return new PersistentPrimHashSet(_data.assocN(pos, o), _meta, _size+1, _free);
		} 
		//Shift elements, recursively if necessary
		else {
			//Temporarily store o in its invalid pos, while we search for a valid exchange
			IPersistentVector newData = _data.assocN(pos, o);
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
		return new PersistentPrimHashSet(_data.assocN(pos, _free), _meta, _size - 1, _free);
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
	
	private class FilteredSeq extends ASeq{
		private static final long serialVersionUID = -4366857561968576225L;
		final int i;
		public FilteredSeq() {
			super(null);
			int pos = 0;
			while(pos < _capacity){
				if (_data.nth(pos).equals(_free)) pos++;
				else {
					i = pos;
					return;
				}
			}
			//Should never be reached, as before constructing we check for empty.
			i = -1;
		}
		private FilteredSeq(int pos, IPersistentMap meta) {
			super(meta);
			i = pos;
		}
		@Override
		public Object first() {
			return _data.nth(i);
		}
		@Override
		public ISeq next() {
			int pos = i+1;
			while(pos < _capacity){
				if (_data.nth(pos).equals(_free)) pos++;
				else return new FilteredSeq(pos, FilteredSeq.this.meta());
			}
			return null;
		}
		@Override
		public Obj withMeta(IPersistentMap meta) {
			return new FilteredSeq(i, meta);
		}
	}
	
	@Override
	public ISeq seq() {
		if(_size==0) return null;
		return new FilteredSeq();
	}
	@Override
	public int count() {
		return _size;
	}
	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentPrimHashSet(_data, meta, _size, _free);
	}
	@Override
	public IPersistentCollection empty() {
		return fromProto(_data, _free);
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
	public IPersistentMap meta() {
		return _meta;
	}	@Override
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