package soac.java.intern;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import clojure.lang.ISeq;

//This maintains a set of seqs; given a new seq it attempts to "replace" as
//much of the tail as possible with shared elements from its pool.  This is 
//useful for, eg, compactly representing ngrams of multiple sizes.
//Effectively this constructs a tree, where "children" (differing prefixes)
//point at their "parents" (common suffixes) rather than vice versa.
//You don't want to hang onto this forever; it should be used transiently to
//build up a working set.
public class InternedSuffixPool {
	final ConcurrentHashMap<ISeq, ISeq> pool = new ConcurrentHashMap<>();
	
	public ISeq intern(ISeq targ){
		if(targ==null) return null;
		
		//Check if we're presently duped
		if(pool.contains(targ)) return pool.get(targ);

		//If not, are we at the end?  Then bail.
		if (targ.next()==null) return targ;

		//Otherwise check next suffix, recursively.
		ISeq out = intern(targ.more()).cons(targ.first());		

		//And add to the pool - during recursive calls this populates 
		//intermediate lists as we unwind.
		pool.putIfAbsent(out, out);
		
		return out;
	}
	
	//For situations where we are strictly going up in size and adding
	//prefixes to existing phrases - eg, going from n-grams to n+1-grams
	public void clearSuffixesShorterThan(int n){
		final Iterator<ISeq> it = pool.keySet().iterator();
		while(it.hasNext()){
			final ISeq s = it.next();
			if(s.count()<n) it.remove();
		}
	}
	
	public void clear(){
		this.pool.clear();
	}
}
