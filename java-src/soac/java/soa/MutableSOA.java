package soac.java.soa;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Comparator;

import soac.java.pav.PersistentArrayVector;
import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Util;

//This does not support concurrent modifications.
public class MutableSOA extends AbstractList<Object> {
	final Object[] data;
	final IFn[] copiers;
	final IFn[] asets;
	final IFn[] agets;
	int filledLength;
	int realLength;
	
	public static final double expansionFactor = 1.25;
	
	public MutableSOA(Object[] data, IFn[] copiers, IFn[] asets, IFn[] agets, int filledLength, int realLength) {
		this.data = data;
		this.copiers = copiers;
		this.asets = asets;
		this.agets = agets;
		this.filledLength = filledLength;
		this.realLength = realLength;
	}
	
	public Object getRowAndCol(int row, int col){
		return agets[col].invoke(data[col], row);
	}
	
	public void expand(){
		int newSize = Math.max(realLength+1, (int)(realLength * expansionFactor));
		for(int i=0; i<data.length; i++){
			data[i] = copiers[i].invoke(data[i], newSize);
		}
		realLength = newSize;
	}
	
	public void trim(){
		for(int i=0; i<data.length; i++){
			data[i] = copiers[i].invoke(data[i], filledLength);
		}
		realLength = filledLength;
	}
	
	@Override
	public Object get(int index) {
		final Object[] stuff = new Object[data.length];
		for(int i=0; i<stuff.length; i++){
			stuff[i] = getRowAndCol(index, i);
		}
		return new PersistentArrayVector(stuff, null);
	}
	@Override
	public int size() {
		return filledLength;
	}
	@Override
	public Object set(int index, Object element) {
		final Object out = get(index);
		for(int i=0; i<data.length; i++){
			asets[i].invoke(data[i], index, RT.get(element, i));
		}
		return out;
	}
	@Override
	public void add(int index, Object element) {
		if(realLength > filledLength + 1){
			for(int col = 0; col < data.length; col++){
				System.arraycopy(data[col], index, data[col], index+1, filledLength - index + 1);
				asets[col].invoke(data[col], index, RT.nth(element, col));
			}
			filledLength++;
		}
		else {
			expand();
			add(index, element);
		}
	}
	@Override
	public Object remove(int index) {
		final Object out = get(index);
		for(int col = 0; col < data.length; col++){
			System.arraycopy(data[col], index+1, data[col], index, filledLength - index - 1);
		}
		filledLength--;
		return out;
	}
	
	public Object[] getRawData(){
		return data;
	}
	public int getRealLength(){
		return realLength;
	}
	public int getFilledLength(){
		return filledLength;
	}
	
	//Sorts roughly in-place based on the given column number.  Uses auxiliary
	//storage needed to sort an array of Integers, and then transient auxiliary
	//space to copy each column.  This avoids destructuring each element
	//repeatedly.
	public void sortInPlaceByCol(final int col){
		final Integer[] idxOrd = new Integer[filledLength];
		for(int i=0;i<idxOrd.length;i++) idxOrd[i]=i;
		Arrays.sort(idxOrd, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return Util.compare(getRowAndCol(o1, col), getRowAndCol(o2, col));
			}
		});
		
		for(int i=0; i<data.length; i++){
			final Object tmp = copiers[i].invoke(data[i], realLength);
			for(int outIndex=0; outIndex<idxOrd.length; outIndex++){
				final int sourceIndex = idxOrd[outIndex];
				final Object sourceVal = agets[i].invoke(data[i], sourceIndex);
				asets[i].invoke(tmp, outIndex, sourceVal);
			}
			data[i] = tmp;
		}
	}
}
