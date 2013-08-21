package soac.java;

import clojure.core.ArrayManager;
import clojure.core.Vec;
import clojure.lang.AFn;
import clojure.lang.Counted;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import clojure.lang.Util;

public abstract class PersistentPrimHashTable extends AFn implements IObj, Counted {
	final IPersistentVector _ks;
	final int _size, _capacity;
	final Object _free;
	final IPersistentMap _meta;
	
	final static int neighborhood = 32;
	final static double rehashThresholdHi = 0.8;
	final static double rehashThresholdLo = 0.25;
	
	public PersistentPrimHashTable(IPersistentVector ks, IPersistentMap meta, int size, Object free) {
		this._ks = ks;
		this._meta = meta;
		this._size = size;
		this._free = free;
		this._capacity = ks.count();
	}
	
	@Override
	public int count() {
		return _size;
	}
	@Override
	public IPersistentMap meta() {
		return _meta;
	}
	public int wrappingInc(int i){
		return (i+1) & (_capacity - 1);
	}
	
	public int bitMod(int i){
		return i & (_capacity - 1);
	}
	
	public int findIndex(Object o){
		int pos = bitMod(o.hashCode());
		int ctr = 0;
		
		if(_ks instanceof Vec){
			final Vec vData = (Vec)_ks;
			final ArrayManager am = (ArrayManager)vData.am;
			Object afor = vData.arrayFor(pos);
			int localPos = pos & 0x1f;
			while(ctr<neighborhood){
				if(Util.equiv(am.aget(afor, localPos),o)) return pos;
				localPos = (localPos + 1) & 31;
				ctr++;
				pos = wrappingInc(pos);
				if(localPos==0) afor = vData.arrayFor(pos);
			}
		} else if(_ks instanceof PersistentVector){
			final PersistentVector vData = (PersistentVector)_ks;
			Object[] afor = vData.arrayFor(pos);
			int localPos = pos & 31;
			while(ctr<neighborhood){
				if(Util.equiv(afor[localPos],o)) return pos;
				localPos = (localPos + 1) & 31;
				ctr++;
				pos = wrappingInc(pos);
				if(localPos==0) afor = vData.arrayFor(pos);
			}
			return -1;
		}
		
		while(ctr<neighborhood){
			if(Util.equiv(_ks.nth(pos),o)) return pos;
			pos = wrappingInc(pos);
			ctr++;
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

	public boolean checkPosition(Object o, int pos){
		if(o == _free) return true;
		final int bottom = bitMod(o.hashCode());
		final int top = bitMod(o.hashCode() + neighborhood);
		if(top > bottom) return (top > pos && pos >= bottom);
		else return (top > pos || pos >= bottom);
	}
	
	public static IPersistentVector exchange(IPersistentVector v, int i, int j){
		Object iObj = v.nth(i);
		return v.assocN(i, v.nth(j)).assocN(j, iObj);
	}
}
