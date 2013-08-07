package soac.java;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import clojure.lang.AFn;
import clojure.lang.APersistentMap;
import clojure.lang.ASeq;
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
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;

//There is a fair amount of duplication between this and PersistentPrimHashSet.  Ideally we'd
//have them both inherit from a PersistentPrimHashTable, but we have to extend AFn unless we want
//to write all the boilerplate to fulfill IFn.
//We could have this extend PersistentPrimHashSet, at the cost of implementing the Collection api via
//a seq of k/v pairs.

@SuppressWarnings("rawtypes")
public class PersistentPrimHashMap extends AFn implements Map, IObj, IPersistentMap, Iterable, IHashEq, MapEquivalence{
	final IPersistentVector _ks, _vs;
	final int _size, _capacity;
	final Object _free;
	final IPersistentMap _meta;
	
	final static int neighborhood = 32;
	final static double rehashThresholdHi = 0.8;
	final static double rehashThresholdLo = 0.25;

	private PersistentPrimHashMap(IPersistentVector ks, IPersistentVector vs, int size, Object free, IPersistentMap meta) {
		this._ks = ks;
		this._vs = vs;
		this._size = size;
		this._free = free;
		this._meta = meta;
		this._capacity = ks.count();
	}
	
	public static PersistentPrimHashMap fromProto(IPersistentVector ks, IPersistentVector vs, Object free){
		IPersistentVector newKs = (IPersistentVector)ks.empty();
		IPersistentVector newVs = (IPersistentVector)vs.empty();
		for(int i = 0; i<neighborhood; i++){
			newKs = newKs.cons(free);
			//Everything can store some version of 0
			newVs = newVs.cons(0);
		}
		return new PersistentPrimHashMap(newKs, newVs, 0, free, null);

	}
	
	public int wrappingInc(int i){
		return (i+1) & (_capacity - 1);
	}
	
	public int bitMod(int i){
		return i & (_capacity - 1);
	}
	
	public int findIndex(Object o){
		int pos = bitMod(o.hashCode());
		for(int i=0; i<neighborhood; i++){
			if(_ks.nth(pos).equals(o)) return pos;
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

	public int getCapacity(){
		return _capacity;
	}
	
	public Object getFree(){
		return _free;
	}
	
	public IPersistentVector getRawKeys(){
		return _ks;
	}
	
	public IPersistentVector getRawVals(){
		return _vs;
	}
	
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
	
	public PersistentPrimHashMap rehash(){
		return rehash(1);
	}
	
	public PersistentPrimHashMap rehash(int increment){
		int newCapacity = _capacity << increment;
		IPersistentVector newKs = (IPersistentVector)_ks.empty();
		IPersistentVector newVs = (IPersistentVector)_vs.empty();
		for(int i=0; i<newCapacity; i++) {
			newKs = newKs.cons(_free);
			newVs = newVs.cons(0);
		}
		
		PersistentPrimHashMap out = new PersistentPrimHashMap(newKs, newVs, 0, _free, _meta);
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
		if(load() > rehashThresholdHi) return rehash().assoc(k,v);

		int pos = bitMod(k.hashCode()); int ct = 0;
		//Find the first free position
		while(!_ks.nth(pos).equals(_free)){
			pos = wrappingInc(pos);
			ct++;
		}
		//pos now represents the first free slot, and ct how far away it is from the insert point
		if(ct<neighborhood) {
			return new PersistentPrimHashMap(_ks.assocN(pos, k), _vs.assocN(pos, v), _size+1, _free, _meta);
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
						return new PersistentPrimHashMap(exchange(_ks, bottomPos, topPos), exchange(_vs, bottomPos, topPos), _size+1, _free, _meta);
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
	
	private class FilteredSeq extends ASeq{
		private static final long serialVersionUID = -4366857561968576225L;
		final int i;
		public FilteredSeq() {
			super(null);
			int pos = 0;
			while(pos < _capacity){
				if (_ks.nth(pos).equals(_free)) pos++;
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
			return new MapEntry(_ks.nth(i), _vs.nth(i));
		}
		@Override
		public ISeq next() {
			int pos = i+1;
			while(pos < _capacity){
				if (_vs.nth(pos).equals(_free)) pos++;
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
		if(pos>=0) return new PersistentPrimHashMap(_ks.assocN(pos,_free), _vs, _size-1, _free, _meta);
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
	public IPersistentMap meta() {
		return _meta;
	}
	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentPrimHashMap(_ks, _vs, _size, _free, meta);
	}
	@Override
	public int size() {
		return _size;
	}
	@Override
	public int count() {
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
