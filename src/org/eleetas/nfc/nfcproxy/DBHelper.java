package org.eleetas.nfc.nfcproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class DBHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "NFCProxyDB";
	private static final String CREATE_REPLAYS_TABLE = "CREATE TABLE replays(_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, transactions BLOB, type INTEGER, built_in INTEGER DEFAULT 0)";
	public static final int REPLAY_TAG = 0;
	public static final int REPLAY_PCD = 1;

	DBHelper(Context context){
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_REPLAYS_TABLE);		
		//built in transactions
		byte[][] vivoPayVisa = new byte[][] {{0x00, (byte)0xa4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00}, {0x00, (byte)0xa4, 0x04, 0x00, 0x07, (byte)0xa0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10, 0x00}, {(byte)0x80, (byte)0xa8, 0x00, 0x00, 0x04, (byte)0x83, 0x02, (byte)0x80, 0x00, 0x00}, {0x00, (byte)0xb2, 0x01, 0x0c, 0x00}};
		byte[][] vivoPayMasterCard = new byte[][] {{0x00, (byte)0xa4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00}, {0x00, (byte)0xa4, 0x04, 0x00, 0x07, (byte)0xa0, 0x00, 0x00, 0x00, 0x04, 0x10, 0x10, 0x00}, {(byte)0x80, (byte)0xa8, 0x00, 0x00, 0x02, (byte)0x83, 0x00, 0x00}, {0x00, (byte)0xb2, 0x01, 0x0c, 0x00}, {(byte)0x80, 0x2a, (byte)0x8e, (byte)0x80, 0x04, 0x00, 0x00, 0x00, 0x29, 0x00}};
		byte[][] vivoPayDiscover = new byte[][] {{0x00, (byte)0xa4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00}, {0x00, (byte)0xa4, 0x04, 0x00, 0x07, (byte)0xa0, 0x00, 0x00, 0x03, 0x24, 0x10, 0x10, 0x00}, {(byte)0x80, (byte)0xa8, 0x00, 0x00, 0x03, (byte)0x83, 0x01, 0x63, 0x00}, {0x00, (byte)0xb2, 0x01, 0x0c, 0x00}};
		byte[][] vivoPayAmex = new byte[][] {{0x00, (byte)0xa4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00}, {0x00, (byte)0xa4, 0x04, 0x00, 0x06, (byte)0xa0, 0x00, 0x00, 0x00, 0x25, 0x01, 0x00}, {(byte)0x80, (byte)0xa8, 0x00, 0x00, 0x02, (byte)0x83, 0x00, 0x00}, {0x00, (byte)0xb2, 0x01, 0x0c, 0x00}, {0x00, (byte)0xb2, 0x02, 0x0c, 0x00}, {0x00, (byte)0xb2, 0x03, 0x0c, 0x00}, {(byte)0x80, (byte)0xca, (byte)0x9f, 0x36, 0x00}, {(byte)0x80, (byte)0xae, (byte)0x80, 0x00, 0x04, 0x00, 0x00, 0x09, 0x03, 0x00}};
		saveTransactions(db, "VivoPay 4000 - Visa", vivoPayVisa, REPLAY_PCD, 1);			
		saveTransactions(db, "VivoPay 4000 - MasterCard", vivoPayMasterCard, REPLAY_PCD, 1);
		saveTransactions(db, "VivoPay 4000 - Discover", vivoPayDiscover, REPLAY_PCD, 1);
		saveTransactions(db, "VivoPay 4000 - American Express", vivoPayAmex, REPLAY_PCD, 1);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub

	}	

	private long saveTransactions(SQLiteDatabase db,String name, byte[][] transactions, int type, int builtIn) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(transactions);
		} catch (IOException e) {
			e.printStackTrace();
		}

		byte[] tBytes = baos.toByteArray();
        SQLiteStatement stmt = db.compileStatement("INSERT OR IGNORE INTO replays (name, transactions , type, built_in) values (?, ?, ?, ?)");
        stmt.bindString(1, name);
        stmt.bindBlob(2, tBytes);
        stmt.bindLong(3, type);
        stmt.bindLong(4, builtIn);
        return stmt.executeInsert();        
	}
	
	public long saveTransactions(String name, byte[][] transactions, int type) {
		SQLiteDatabase wDB = getWritableDatabase();
		return saveTransactions(wDB, name, transactions, type, 0);
	}
	
	public Cursor getReplays() {
		  SQLiteDatabase rDB = getReadableDatabase(); 		
		  return rDB.query("replays", null, null, null, null, null, null);
	}
	
	public int deleteReplay(String name) {
		SQLiteDatabase wDB = getWritableDatabase();
		SQLiteStatement stmt = wDB.compileStatement("DELETE FROM replays WHERE name = ? AND built_in = 0");
		stmt.bindString(1, name);
		return stmt.executeUpdateDelete();
	}
}
