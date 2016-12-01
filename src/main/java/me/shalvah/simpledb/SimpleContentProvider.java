package me.shalvah.simpledb;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * ContentProvider with methods and variables implemented
 */
public abstract class SimpleContentProvider extends ContentProvider
	{
		/**
		 * The _id column present in each table
		 */
		public static final String COLUMN_ID = "_id";

		/**
		 * Uri Matcher used to match content uris
		 */
		private static final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

		/**
		 * The provider name
		 */
		public static String PROVIDER_NAME = "me.shalvah.simpledb.dataprovider";

		/**
		 * Base paths for tables in the schema
		 */
		public static ArrayList<String> BASE_PATH = new ArrayList<>();

		/**
		 * Content uris for tables in the schema
		 */
		public static ArrayList<Uri> CONTENT_URI = new ArrayList<>();

		/**
		 * Integer constants used to match uris for different tables in the schema and their
		 * corresponding "_ID" address
		 */
		public static HashMap<String, Integer> uriMatcherStrings = new HashMap<String, Integer>();

		/**
		 * The database schema model
		 */
		public static Schema db;

		/**
		 * The actual SQLite database
		 */
		private static SQLiteDatabase sqldb;

		/**
		 * Gets the content uri generated for a given string.
		 *
		 * @param tableName either the name of the table ("items") or the table name followed by a
		 *                    slash, then an id ("items/3")
		 * @return the uri for the reource
		 */
		public static Uri contentUri(String tableName)
		{
			if (tableName.contains("/"))
			{
				String realName = tableName.substring(0, tableName.indexOf("/"));
				String id = tableName.substring(tableName.indexOf("/") + 1);
				return Uri.parse("content://" + PROVIDER_NAME + "/" +
						realName + "/" + id);
			}
			return Uri.parse("content://" + PROVIDER_NAME + "/" +
					tableName);
		}

		/**
		 * Creates the database schema and sets up base paths, content uris and uri matching
		 *
		 * @param providerName the package name of the content provider
		 * @param dbName the desired name of the database
		 * @param dbVersion the version number of the database. Increment this whenever you make
		 *                    changes to your schema (change tables or columns)
		 * @param tables the tables to be created in the database
		 */
		public void init(String providerName, String dbName, int dbVersion, Table... tables)
		{
			PROVIDER_NAME = providerName;

			//init schema
			db = new Schema(getContext(), dbName, dbVersion, tables);

			//setup base paths and content uris
			for (int i = 0; i < tables.length; i++)
			{
				Table t = tables[i];
				BASE_PATH.add(t.name());
				CONTENT_URI.add(Uri.parse("content://" + PROVIDER_NAME + "/" + BASE_PATH.get(i)));
				uriMatcherStrings.put(t.name(), (int) Math.pow(10, i));
				uriMatcherStrings.put(t.name() + "_ID", ((int) Math.pow(10, i)) * 2);
			}

			//setup uri matcher
			for (int i = 0; i < BASE_PATH.size(); i++)
			{
				String tname = BASE_PATH.get(i);
				matcher.addURI(PROVIDER_NAME, tname, uriMatcherStrings.get(tname));
				matcher.addURI(PROVIDER_NAME, tname + "/#", uriMatcherStrings.get(tname + "_ID"));
			}
		}

		@Override
		public boolean onCreate()
		{
			//all columns and tables must be created before this is run!
			setup();
			sqldb = db.create();
			return (sqldb != null);
		}

		/**
		 * Define a schema for your database in terms of tables and columns
		 */
		abstract public void setup();

		@Nullable
		@Override
		public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
		{
			SQLiteQueryBuilder qBuild = new SQLiteQueryBuilder();

			int uriType = matcher.match(uri);
			try
			{
				checkColumns(projection, uriType);
			} catch (IllegalArgumentException e)
			{
				e.printStackTrace();
			}

			for (String s :
					uriMatcherStrings.keySet())
			{
				if (uriMatcherStrings.get(s).equals(uriType))
				{
					qBuild.setTables(s);
					if (s.endsWith("_ID"))
					{
						qBuild.appendWhere("_id" + "="
								+ uri.getLastPathSegment());
					}
					Cursor cursor = qBuild.query(sqldb, projection, selection, selectionArgs, null, null,
							sortOrder);
					cursor.setNotificationUri(getContext().getContentResolver(), uri);
					return cursor;
				}
			}
			throw new IllegalArgumentException("Unknown uri: " + uri);
		}

		@Nullable
		@Override
		public String getType(Uri uri)
		{
			return null;
		}

		@Nullable
		@Override
		public Uri insert(Uri uri, ContentValues values)
		{
			int uriType = matcher.match(uri);
			long id;

			for (String s :
					uriMatcherStrings.keySet())
			{
				if (uriMatcherStrings.get(s).equals(uriType))
				{
					id = sqldb.insert(s, null, values);
					getContext().getContentResolver().notifyChange(uri, null);
					return Uri.parse(s + "/" + id);
				}
			}
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		@Override
		public int delete(Uri uri, String selection, String[] selectionArgs)
		{
			int uriType = matcher.match(uri);
			int rowsDeleted;
			String id;

			for (String s :
					uriMatcherStrings.keySet())
			{
				if (uriMatcherStrings.get(s).equals(uriType))
				{
					if (s.endsWith("_ID"))
					{
						id = uri.getLastPathSegment();
						if (TextUtils.isEmpty(selection))
						{
							rowsDeleted = sqldb.delete(s.replace("_ID", ""), "_id " + "="
									+ id, null);
						} else
						{
							rowsDeleted = sqldb.delete(s.replace("_ID", ""), "_id " + "="
									+ id + " and " + selection, null);
						}
					} else
					{
						rowsDeleted = sqldb.delete(s, selection, selectionArgs);
					}
					getContext().getContentResolver().notifyChange(uri, null);
					return rowsDeleted;
				}
			}
			throw new IllegalArgumentException("Unknown uri: " + uri);
		}


		@Override
		public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
		{
			int rowsUpdated;
			String id;
			int uriType = matcher.match(uri);


			for (String s :
					uriMatcherStrings.keySet())
			{
				if (uriMatcherStrings.get(s).equals(uriType))
				{
					if (s.endsWith("_ID"))
					{
						id = uri.getLastPathSegment();
						if (TextUtils.isEmpty(selection))
						{
							rowsUpdated = sqldb.update(s.replace("_ID", ""), values, "_id" + "=" + id, null);
						} else
						{
							rowsUpdated = sqldb.update(s.replace("_ID", ""), values, "_id" + "=" + id + " and " + selection,
									selectionArgs);
						}
					} else
					{
						rowsUpdated = sqldb.update(s, values, selection,
								selectionArgs);
					}
					getContext().getContentResolver().notifyChange(uri, null);
					return rowsUpdated;
				}
			}
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		/**
		 * Ensures the columns to which access has been requested exist in the table
		 *
		 * @param projection the requested columns
		 * @param uriType the type of the uri requested
		 */
		private void checkColumns(String[] projection, int uriType)
		{
			HashSet<String> requestedColumns;
			HashSet<String> availableColumns;

			String[] available = new String[0];
			for (String s :
					uriMatcherStrings.keySet())
			{
				if (uriMatcherStrings.get(s).equals(uriType))
				{
					available = db.table(s.replace("_ID", "")).getAllColumns();
					break;
				}
			}
			if (available.length == 0)
			{
				throw new IllegalArgumentException("Could not match uri type: " + uriType);
			}

			if (projection != null)
			{
				requestedColumns = new HashSet<String>(Arrays.asList(projection));
				availableColumns = new HashSet<String>(Arrays.asList(available));
				if (!availableColumns.containsAll(requestedColumns))
				{
					throw new IllegalArgumentException("Unknown columns in projection");
				}
			}

		}
	}
