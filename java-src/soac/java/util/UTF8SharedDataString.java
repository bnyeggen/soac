package soac.java.util;

import java.util.Arrays;

public class UTF8SharedDataString extends UTF8String {
	final int offset;
	final int byteLength;
		
	public UTF8SharedDataString(byte[] data, int offset, int byteLength) {
		super(data);
		this.offset = offset;
		this.byteLength = byteLength;
	}
	
	public UTF8SharedDataString(String s){
		super(s);
		offset = 0;
		byteLength = data.length;
	}
	
	public UTF8SharedDataString(CharSequence c) {
		this(c.toString());
	}	
	
	public byte[] usedBytes(){
		return Arrays.copyOfRange(data, offset,offset+byteLength);
	}
	
	//This refers to whole codepoints, not chars as for subSequence
	@Override
	public UTF8SharedDataString sharedSubSequence(int start, int end){
		//1, 2, and 3-byte UTF8 sequences are represented by a single UTF16 char
		//4-byte sequences are represented as a surrogate pair
		int bytePos = offset;
		int ct = 0;
		while(ct<start){
			bytePos += bytesInCodepointAt(bytePos);
			ct++;
		}
		final int startBytePos = bytePos;
		while(ct<end){
			bytePos += bytesInCodepointAt(bytePos);
			ct++;
		}
		final int endBytePos = bytePos;
		return new UTF8SharedDataString(data, startBytePos, endBytePos-startBytePos);
	}
	
	@Override
	public String toString() {
		return new String(usedBytes(), utf8);
	}
	
}