package org.eleetas.nfc.nfcproxy.utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.nfc.Tag;
import android.nfc.tech.TagTechnology;


//TODO: HACK since BasicTagTechnology is not currently visible from SDK. primiarly want the transceive() method (otherwise we could just use TagTechnology) 
//TODO: Just access BasicTagTechnolgy directly but modifying platform library android.jar. (use library with hidden classes from framework.jar)
public class BasicTagTechnologyWrapper implements TagTechnology {

//	Method get;
	Method transceive;
	Method isConnected;
	Method connect;
	//Method getMaxTransceiveLength;
	Method close;
	Tag mTag;
	Object mTagTech;
	
	public BasicTagTechnologyWrapper(Tag tag, String tech) throws ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Class cls = Class.forName(tech);
		Method get = cls.getMethod("get", Tag.class);
		mTagTech = get.invoke(null, tag);
		transceive = cls.getMethod("transceive", byte[].class);
		isConnected = cls.getMethod("isConnected");
		connect = cls.getMethod("connect");
		//getMaxTransceiveLength = cls.getMethod("getMaxTransceiveLength");
		close = cls.getMethod("close");
		mTag = tag;
	}

	@Override
	public boolean isConnected() {
		Boolean ret = false;
		try {
			ret = (Boolean) isConnected.invoke(mTagTech);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			}
			e.printStackTrace();
		}
		return ret;
	}
	
	@Override
	public void connect() throws IOException {
		try {
			connect.invoke(mTagTech);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			}
			else if (e.getTargetException() instanceof IOException) {
				throw (IOException) e.getTargetException();
			}
			e.printStackTrace();
		}
	}
	
	@Override
	public void close() throws IOException {
		try {
			close.invoke(mTagTech);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			}
			else if (e.getTargetException() instanceof IOException) {
				throw (IOException) e.getTargetException();
			}
			e.printStackTrace();
		}
	}	
	
	/*
	public int getMaxTransceiveLength() {
    	try {
			return (Integer)getMaxTransceiveLength.invoke(mTagTech);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			}
			e.printStackTrace();
		}
		return 0;
    }
	*/
	public byte[] transceive(byte[] data) throws IOException {
		try {
			return (byte[])transceive.invoke(mTagTech, data);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			}
			else if (e.getTargetException() instanceof IOException) {
				throw (IOException) e.getTargetException();
			}
			e.printStackTrace();
System.err.println(e);
System.err.println(e.getTargetException());
		}
		throw new IOException("transceive failed");
	}

	@Override
	public Tag getTag() {
		return mTag;
	}
/*
	@Override
	public void reconnect() throws IOException {
		// TODO Auto-generated method stub
		
	}
*/
}
