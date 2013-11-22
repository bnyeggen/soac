package soac.java.intern;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import clojure.lang.RT;


//(import 'soac.java.intern.VectorPrefixPool)
//(defn half-rand [n] 
//(let [half (int (/ n 2))]
//	  (into (vec (range half))
//	        (repeatedly half #(rand-int half)))))
//(def a (VectorPrefixPool.))
//;~2.5gb
//(def b (vec (repeatedly 10000 #(half-rand 10000))))
//;~1gb
//(def b (vec (repeatedly 10000 #(.intern a (half-rand 10000)))))

public class VectorPrefixPool {
	final ConcurrentHashMap<IPersistentVector, List<IPersistentVector>> pool;
	public static final int MIN_PREFIX_LENGTH = 32;
	public static final int MAX_PREFIX_LENGTH = 256;
	
	public VectorPrefixPool() {
		pool = new ConcurrentHashMap<>();
	}
	
	public static int sharedPrefixLength(int startPos, IPersistentVector v1, IPersistentVector v2){
		int i = startPos;
		while(i<v1.count() && i<v2.count() && v1.nth(i).equals(v2.nth(i))) i++;
		return i;
	}
	
	public IPersistentVector intern(IPersistentVector v){
		for(int prefixLength = Math.min(MAX_PREFIX_LENGTH, Integer.highestOneBit(v.length())); 
				prefixLength >= MIN_PREFIX_LENGTH && prefixLength <= v.length(); 
				prefixLength /=2){
			final IPersistentVector prefix = RT.subvec(v, 0, prefixLength);
			if(pool.putIfAbsent(prefix, new ArrayList<IPersistentVector>())!=null){
				IPersistentVector longestMatch = PersistentVector.EMPTY;
				int longestMatchLength = 0;
				
				//Find actual longest matching vector
				for (final IPersistentVector prospective : pool.get(prefix)){
					final int matchLength = sharedPrefixLength(prefixLength, v, prospective);
					if(matchLength > longestMatchLength){
						longestMatchLength = matchLength;
						longestMatch = prospective; // The entire vector, not just matching component
					}
				}
				//Build up output from shared prefix with that vector
				longestMatch = RT.subvec(longestMatch, 0, longestMatchLength);
				for(int i=longestMatchLength; i<v.length(); i++){
					longestMatch = longestMatch.cons(v.nth(i));
				}
				
				//Add suffixes as potential matches 
				for(prefixLength*=2; prefixLength <= MAX_PREFIX_LENGTH; prefixLength*=2){
					IPersistentVector subvec = RT.subvec(v, 0, prefixLength);
					pool.putIfAbsent(subvec, new ArrayList<IPersistentVector>());
					pool.get(subvec).add(v);

				}
				//Return the output
				return longestMatch;
			} else {
				pool.get(prefix).add(v);
			}
		}
		return v;
	}
	
	public ConcurrentHashMap<IPersistentVector, List<IPersistentVector>> getPool(){
		return pool;
	}
}