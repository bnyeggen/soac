package soac.java.soa;

import java.util.concurrent.atomic.AtomicInteger;

import soac.java.pav.PersistentArrayVector;

import clojure.lang.APersistentVector;
import clojure.lang.IFn;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentStack;
import clojure.lang.IPersistentVector;
import clojure.lang.RT;

public class ImmutableArraySOA extends APersistentVector {
	public static final long serialVersionUID = 1L;
	
	final Object[] data;
	final IFn[] copiers;
	final IFn[] asets;
	final IFn[] agets;
	final AtomicInteger sharedMaxFilledLength;
	final int filledLength;
	final int realLength;
	
	public final static double expansionFactor = 1.25;
	
	public ImmutableArraySOA(Object[] data, IFn[] copiers, IFn[] asets, IFn[] agets, AtomicInteger sharedMaxFilledLength, int filledLength, int realLength) {
		this.data = data;
		this.copiers = copiers;
		this.asets = asets;
		this.agets = agets;
		this.sharedMaxFilledLength = sharedMaxFilledLength;
		this.filledLength = filledLength;
		this.realLength = realLength;
	}
	
	ImmutableArraySOA withNewData(Object[] data, AtomicInteger sharedMaxFilledLength, int filledLength, int realLength){
		return new ImmutableArraySOA(data, copiers, asets, agets, sharedMaxFilledLength, filledLength, realLength);
	}
	
	public ImmutableArraySOA expand(){
		final int newLength = Math.max(realLength + 1, (int)(realLength * expansionFactor));
		final Object[] newData = new Object[data.length];
		for(int i=0; i<newData.length; i++){
			newData[i] = copiers[i].invoke(data[i], newLength);
		}
		return withNewData(newData, new AtomicInteger(filledLength), filledLength, newLength);
	}
	
	public ImmutableArraySOA trim(){
		final Object[] newData = new Object[data.length];
		for(int i=0; i<newData.length; i++){
			newData[i] = copiers[i].invoke(data[i], filledLength);
		}
		return withNewData(newData, new AtomicInteger(filledLength), filledLength, filledLength);
	}
	
	@Override
	public IPersistentVector assocN(int idx, Object o) {
		//Appending to end of shared data
		if(idx==filledLength && sharedMaxFilledLength.compareAndSet(filledLength, filledLength+1)){
			if(!(realLength > filledLength + 1)) return expand().assocN(idx, o);
			for(int i=0; i<data.length; i++){
				asets[i].invoke(data[i], idx, RT.nth(o, i));
			}
			//Already modified sharedMaxFilledLength above
			return withNewData(data, sharedMaxFilledLength, filledLength+1, realLength);
		} 
		//Middle edit
		else {
			final Object[] newData = new Object[data.length];
			for(int i=0; i<newData.length; i++){
				newData[i] = copiers[i].invoke(data[i], realLength);
			}
			return withNewData(newData, new AtomicInteger(filledLength), filledLength, realLength).assocN(idx, o);
		}
	}

	@Override
	public IPersistentVector cons(Object o) {
		return assocN(filledLength, o);
	}

	@Override
	public int count() {
		return filledLength;
	}

	@Override
	public IPersistentCollection empty() {
		final Object[] newData = new Object[data.length];
		for(int i=0; i<newData.length; i++){
			newData[i] = copiers[i].invoke(data[i], 32);
		}
		return withNewData(newData, sharedMaxFilledLength, 0, 32);
	}

	//Hangs onto the backing data.
	@Override
	public IPersistentStack pop() {
		return withNewData(data, sharedMaxFilledLength, filledLength-1, realLength);
	}

	@Override
	public Object nth(int index) {
		final Object[] stuff = new Object[data.length];
		for(int i=0; i<stuff.length; i++){
			stuff[i] = agets[i].invoke(data[i], index);
		}
		return new PersistentArrayVector(stuff, null);
	}
	
}
