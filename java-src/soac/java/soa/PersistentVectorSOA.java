package soac.java.soa;

import soac.java.pav.PersistentArrayVector;
import clojure.lang.APersistentVector;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentStack;
import clojure.lang.IPersistentVector;
import clojure.lang.RT;

public class PersistentVectorSOA extends APersistentVector {
	public static final long serialVersionUID = 1L;
	final IPersistentVector[] columns;
	
	public PersistentVectorSOA(IPersistentVector[] columns) {
		this.columns = columns;
	}
	
	@Override
	public IPersistentVector assocN(int i, Object o) {
		final IPersistentVector[] newCols = new IPersistentVector[columns.length];
		for(int ctr=0; ctr< newCols.length; ctr++){
			newCols[ctr] = columns[ctr].assocN(i, RT.nth(o, ctr));
		}
		return new PersistentVectorSOA(newCols);
	}

	@Override
	public IPersistentVector cons(Object o) {
		final IPersistentVector[] newCols = new IPersistentVector[columns.length];
		for(int ctr=0; ctr< newCols.length; ctr++){
			newCols[ctr] = columns[ctr].cons(RT.nth(o, ctr));
		}
		return new PersistentVectorSOA(newCols);
	}

	@Override
	public int count() {
		return columns[0].count();
	}

	@Override
	public IPersistentCollection empty() {
		IPersistentVector[] newCols = new IPersistentVector[columns.length];
		for(int i=0; i<columns.length; i++){
			newCols[i] = (IPersistentVector)columns[i].empty();
		}
		return new PersistentVectorSOA(newCols);
	}

	@Override
	public IPersistentStack pop() {
		final IPersistentVector[] newCols = new IPersistentVector[columns.length];
		for(int ctr=0; ctr< newCols.length; ctr++){
			newCols[ctr] = (IPersistentVector)columns[ctr].pop();
		}
		return new PersistentVectorSOA(newCols);
	}

	@Override
	public Object nth(int idx) {
		final Object[] stuff = new Object[columns.length];
		for(int i=0;i<stuff.length;i++){
			stuff[i] = columns[i].nth(idx);
		}
		return new PersistentArrayVector(stuff,null);
	}

}
