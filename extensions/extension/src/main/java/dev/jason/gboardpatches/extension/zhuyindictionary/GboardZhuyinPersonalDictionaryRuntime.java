package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import java.lang.reflect.*;
import java.util.*;

/** Adds dictionary words to the actual native contacts-import transaction. */
public final class GboardZhuyinPersonalDictionaryRuntime {
    static final String OWNED = "zhuyin_personal_dictionary_owned_words_v1";
    private static final String TAG = "GboardZhuyinDictionary";
    static final String WORDS = "zhuyin_contacts_dictionary_words_v2";
    static final String REVISION = "zhuyin_contacts_dictionary_revision_v2";
    static final String APPLIED = "zhuyin_contacts_dictionary_applied_v2";
    static final String LAST_COUNT = "zhuyin_contacts_dictionary_count_v2";
    private static final Object SYNC_LOCK = new Object();
    private static final ThreadLocal<ContactsImport> CONTACTS = new ThreadLocal<>();
    private static final class ContactsImport {
        final Object listener, factory;
        final SharedPreferences preferences;
        final long revision;
        int added;
        ContactsImport(Object listener, Object factory, SharedPreferences preferences) {
            this.listener=listener; this.factory=factory; this.preferences=preferences;
            this.revision=preferences.getLong(REVISION,0);
        }
    }

    static final ExecutorService REFRESH = Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"GboardDictionaryContactsRefresh");thread.setDaemon(true);return thread;
    });

    private static Set<String> readDictionary(Context context) {
        File file=context.getDatabasePath("PersonalDictionary.db");
        Set<String> words=new LinkedHashSet<>();
        if(!file.exists()) return words;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,
                    SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                Cursor cursor=db.rawQuery("SELECT word, locale FROM entry",null)) {
            while(cursor.moveToNext()) {
                String word=cursor.isNull(0)?"":cursor.getString(0).trim();
                String locale=cursor.isNull(1)?null:cursor.getString(1);
                if(accepts(word,locale))words.add(word);
            }
        }
        return words;
    }

    private static void publishSnapshot(Context context, boolean force) {
        Set<String> words=readDictionary(context);
        synchronized(SYNC_LOCK) {
            SharedPreferences preferences=GboardPatchesSettings.preferences(context);
            if(force || !preferences.contains(WORDS)
                    || !words.equals(preferences.getStringSet(WORDS,Collections.emptySet()))) {
                long revision=preferences.getLong(REVISION,0)+1;
                if(!preferences.edit().putStringSet(WORDS,words).putLong(REVISION,revision)
                        .putInt(LAST_COUNT,-1).commit())throw new IllegalStateException("Cannot publish dictionary snapshot");
            }
        }
    }

    public static void personalDictionaryChanged(Object manager) {
        try {
            Context context=(Context)field(manager.getClass(),"e").get(manager);
            requestRefresh(context);
        } catch(ReflectiveOperationException|RuntimeException error) {
            Log.w(TAG,"Cannot observe dictionary change",error);
        }
    }

    static boolean accepts(String word, String locale) {
        if (locale != null && !locale.isEmpty() && !locale.equals("zh")
                && !locale.startsWith("zh_") && !locale.startsWith("zh-")) return false;
        if (word == null || word.isEmpty() || word.codePointCount(0, word.length()) > 128) return false;
        for (int offset = 0; offset < word.length();) {
            int cp = word.codePointAt(offset);
            if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN) return false;
            offset += Character.charCount(cp);
        }
        return true;
    }

    public static void mergeContacts(Object listener) {
        try {
            Object factory = field(listener.getClass(),"a").get(listener);
            if (!"kkb".equals(factory.getClass().getName())) return;
            Context context = (Context)field(factory.getClass(),"j").get(factory);
            // Contacts imports also read the current DB, so startup cannot use a stale saved snapshot.
            try { publishSnapshot(context,false); }
            catch(RuntimeException error) {Log.w(TAG,"Cannot refresh dictionary snapshot during contacts import",error);}
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            if(!preferences.getStringSet(OWNED,Collections.emptySet()).isEmpty()) {
                try {cleanupLegacyUserDictionary(factory,preferences);}
                catch(ReflectiveOperationException|RuntimeException error) {Log.w(TAG,"Cannot clean up previous prototype",error);}
            }
            Object wrapper = field(listener.getClass(),"b").get(listener);
            if (wrapper == null) return; // No active stock contacts transaction.
            ContactsImport run = new ContactsImport(listener,factory,preferences);
            CONTACTS.set(run);
            if (!GboardZhuyinDictionarySettings.readEnabled(preferences)) return;
            Object accessor=field(wrapper.getClass(),"a").get(wrapper);
            if (accessor==null) return;
            Object handle=field(accessor.getClass(),"a").get(accessor);
            long pointer=((Number)method(handle.getClass(),"a").invoke(handle)).longValue();
            Object[] entries=(Object[])method(accessor.getClass(),"nativeExportAllEntries",long.class).invoke(null,pointer);
            if(entries==null) throw new IllegalStateException("Cannot inspect contacts transaction");
            Set<String> existing=new HashSet<>();
            for(Object entry:entries) existing.add((String)field(entry.getClass(),"c").get(entry));
            Set<String> additions=new LinkedHashSet<>();
            for(String word:preferences.getStringSet(WORDS,Collections.emptySet())) {
                if(!accepts(word,null))continue;
                additions.add(word);
                // Use the stock Chinese contact-name splitter (including its suffixes).
                List<String> names=new ArrayList<>();
                method(listener.getClass(),"a",String.class,List.class).invoke(listener,word,names);
                additions.addAll(names);
            }
            for(String word:additions) {
                if(existing.contains(word))continue; // Preserve real-contact weights.
                Object added=method(accessor.getClass(),"nativeInsertOrUpdate",long.class,String.class,int.class,boolean.class)
                        .invoke(null,pointer,word,1,false);
                if(Boolean.TRUE.equals(added)) {existing.add(word);run.added++;}
            }
        } catch(ReflectiveOperationException|RuntimeException error) {
            CONTACTS.remove();
            Log.w(TAG,"Cannot merge personal dictionary into contacts transaction",error);
        }
    }

    public static void contactsFinished(Object listenerOrFactory) {
        ContactsImport run=CONTACTS.get();
        if(run==null || (run.listener!=listenerOrFactory && run.factory!=listenerOrFactory))return;
        CONTACTS.remove();
        // Stock hbf.c() recycles p0 to its factory only after persistence succeeds and L(2) is sent.
        if(listenerOrFactory==run.factory) {
            int count=run.preferences.getLong(REVISION,0)==run.revision ? run.added : -1;
            run.preferences.edit().putLong(APPLIED,run.revision).putInt(LAST_COUNT,count).commit();
        } else {
            run.preferences.edit().putInt(LAST_COUNT,-2).commit();
        }
    }

    public static void contactsAborted(Object listener) {
        ContactsImport run=CONTACTS.get();
        if(run!=null && run.listener==listener) CONTACTS.remove();
    }

    public static boolean contactsRebuildRequired(Object manager) {
        try {
            Context context=(Context)field(manager.getClass(),"e").get(manager);
            SharedPreferences preferences=GboardPatchesSettings.preferences(context);
            return preferences.getLong(REVISION,0)>preferences.getLong(APPLIED,0);
        } catch(ReflectiveOperationException|RuntimeException error) { return false; }
    }

    private static boolean requestContactsRefresh(Context context) {
        try {
            Class<?> type=Class.forName("ndd",false,context.getClassLoader());
            Object manager=method(type,"w",Context.class).invoke(null,context);
            method(manager.getClass(),"A").invoke(manager); // Invalidate stock count/time cache.
            method(manager.getClass(),"p").invoke(manager);
            return true;
        } catch(ReflectiveOperationException|RuntimeException error) {
            Log.w(TAG,"Cannot rebuild contacts suggestions",error); return false;
        }
    }

    public static boolean requestRefresh(Context context) {
        if(context==null)return false;
        Context app=context.getApplicationContext();
        Context source=app!=null?app:context;
        try {
            REFRESH.execute(() -> {
                try {
                    publishSnapshot(source,true);
                    requestContactsRefresh(source);
                } catch(RuntimeException error) {
                    GboardPatchesSettings.preferences(source).edit().putInt(LAST_COUNT,-2).commit();
                    Log.w(TAG,"Cannot rebuild contacts after dictionary update",error);
                }
            });
            return true;
        } catch(RuntimeException error) {Log.w(TAG,"Cannot schedule dictionary refresh",error);return false;}
    }

    private static void cleanupLegacyUserDictionary(Object factory, SharedPreferences preferences)
            throws ReflectiveOperationException {
        ClassLoader loader=factory.getClass().getClassLoader();
        Object wrapper=Class.forName("hbi",false,loader)
                .getConstructor(Class.forName("hbd",false,loader),int.class).newInstance(factory,3);
        try {
            Object accessor=field(wrapper.getClass(),"a").get(wrapper);
            if(accessor==null)throw new IllegalStateException("Old dictionary unavailable");
            Object handle=field(accessor.getClass(),"a").get(accessor);
            long pointer=((Number)method(handle.getClass(),"a").invoke(handle)).longValue();
            Object[] entries=(Object[])method(accessor.getClass(),"nativeExportAllEntries",long.class).invoke(null,pointer);
            if(entries==null)throw new IllegalStateException("Cannot inspect old dictionary");
            Set<String> existing=new HashSet<>();
            for(Object entry:entries)existing.add((String)field(entry.getClass(),"c").get(entry));
            boolean changed=false;
            for(String word:preferences.getStringSet(OWNED,Collections.emptySet())) {
                if(existing.contains(word)) {
                    if(!Boolean.TRUE.equals(method(accessor.getClass(),"a",String.class).invoke(accessor,word)))
                        throw new IllegalStateException("Cannot remove old prototype term");
                    changed=true;
                }
            }
            if(changed) {
                if(!Boolean.TRUE.equals(method(wrapper.getClass(),"b").invoke(wrapper)))
                    throw new IllegalStateException("Cannot persist prototype cleanup");
                method(factory.getClass(),"L",int.class).invoke(factory,3);
            }
            preferences.edit().remove(OWNED).commit();
        } finally { method(wrapper.getClass(),"close").invoke(wrapper); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field f = current.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method m = current.getDeclaredMethod(name, parameters); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private GboardZhuyinPersonalDictionaryRuntime() {}
}
