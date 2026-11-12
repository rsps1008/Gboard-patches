package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import java.util.concurrent.TimeUnit;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class GboardZhuyinPersonalDictionaryRuntimeTest {
    Context context; Object factory;
    static final class ContactsListener {
        private final Object a, b;
        ContactsListener(Object owner) throws Exception {
            a=owner; b=Class.forName("hbi").getConstructor(Class.forName("hbd"),int.class).newInstance(owner,2);
            b.getClass().getMethod("a").invoke(b); // Stock g() starts a fresh contacts dictionary.
            @SuppressWarnings("unchecked") Map<String,Integer> names=(Map<String,Integer>)owner.getClass().getField("realContacts").get(owner);
            for(Map.Entry<String,Integer> name:names.entrySet()) b.getClass().getMethod("c",String.class,int.class).invoke(b,name.getKey(),name.getValue());
        }
        public void a(String word,List<String> names) {names.add(word);if(word.length()>=3)names.add(word.substring(word.length()-2));}
        boolean finish() throws Exception {
            GboardZhuyinPersonalDictionaryRuntime.mergeContacts(this);
            boolean success=(Boolean)b.getClass().getMethod("b").invoke(b);
            b.getClass().getMethod("close").invoke(b);
            if(success)a.getClass().getMethod("L",int.class).invoke(a,2);
            GboardZhuyinPersonalDictionaryRuntime.contactsFinished(success?a:this);
            return success;
        }
    }
    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication();awaitRefresh();prefs().edit().clear().commit();
        context.deleteDatabase("PersonalDictionary.db");
        factory=Class.forName("kkb").getConstructor().newInstance();factory.getClass().getField("j").set(factory,context);
        Class.forName("ndd").getField("instance").set(null,null);
    }
    SharedPreferences prefs(){return GboardPatchesSettings.preferences(context);}
    @SuppressWarnings("unchecked") Map<String,Integer> map(String name)throws Exception{return (Map<String,Integer>)factory.getClass().getField(name).get(factory);}
    Object manager()throws Exception{return Class.forName("ndd").getMethod("w",Context.class).invoke(null,context);}
    void writeRows(Object[][] rows) {
        try(SQLiteDatabase db=context.openOrCreateDatabase("PersonalDictionary.db",0,null)) {
            db.execSQL("CREATE TABLE IF NOT EXISTS entry(word TEXT,locale TEXT,shortcut TEXT)");
            db.beginTransaction();
            try {
                db.execSQL("DELETE FROM entry");
                for(Object[] row:rows)db.execSQL("INSERT INTO entry(word,locale,shortcut) VALUES(?,?,?)",new Object[]{row[0],row[2],row[3]});
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
        }
    }
    void awaitRefresh()throws Exception{GboardZhuyinPersonalDictionaryRuntime.REFRESH.submit(()->{}).get(10,TimeUnit.SECONDS);}
    void snapshot(String... words)throws Exception{
        Object[][] rows=new Object[words.length][];
        for(int i=0;i<words.length;i++)rows[i]=new Object[]{words[i],255,null,null};
        writeRows(rows);
        Object manager=Class.forName("qci").getMethod("B",Context.class).invoke(null,context);
        GboardZhuyinPersonalDictionaryRuntime.personalDictionaryChanged(manager);
        awaitRefresh();
    }
    @Test public void blankAndArbitraryShortcutsAllEnterTheActualContactsTransaction()throws Exception{
        map("realContacts").put("真正姓名",10);map("userWords").put("已學習詞",900);
        Object[][] rows={{"訂飲料",255,null,null},{"買早餐",255,"zh_TW",""},{"台北車站",255,"zh-TW","latin"},{"訂飲料",255,null,"ㄉ"},{"排除詞條",255,"en_US",null},{"english",255,null,null}};
        writeRows(rows); assertTrue(GboardZhuyinPersonalDictionaryRuntime.requestRefresh(context));awaitRefresh();
        assertEquals(new HashSet<>(Arrays.asList("訂飲料","買早餐","台北車站")),prefs().getStringSet(GboardZhuyinPersonalDictionaryRuntime.WORDS,null));
        assertTrue(map("contacts").isEmpty()); assertTrue(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
        assertTrue(new ContactsListener(factory).finish());
        assertTrue(map("contacts").keySet().containsAll(Arrays.asList("真正姓名","訂飲料","飲料","買早餐","台北車站")));
        assertEquals(Collections.singletonMap("已學習詞",900),map("userWords"));
        assertEquals(2,factory.getClass().getField("openedType").getInt(factory));
        assertFalse(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
    }
    @Test public void everyDictionaryChangeInvalidatesCacheEvenWithIdenticalRealContactCount()throws Exception{
        map("realContacts").put("真正姓名",10);
        snapshot("訂飲料");new ContactsListener(factory).finish();
        snapshot("訂飲料","買早餐");assertTrue(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));new ContactsListener(factory).finish();
        assertTrue(map("contacts").containsKey("買早餐"));
        snapshot("買早餐");new ContactsListener(factory).finish();
        assertFalse(map("contacts").containsKey("訂飲料")); assertFalse(map("contacts").containsKey("飲料"));
        assertEquals(Integer.valueOf(10),map("contacts").get("真正姓名"));
        snapshot();new ContactsListener(factory).finish();assertEquals(Collections.singletonMap("真正姓名",10),map("contacts"));
        assertEquals(4,manager().getClass().getField("invalidations").getInt(manager()));
        assertEquals(4,manager().getClass().getField("requests").getInt(manager()));
    }
    @Test public void closingAndReopeningSwitchRebuildsStockListAndPreservesRealContactWeights()throws Exception{
        map("realContacts").put("訂飲料",70);map("realContacts").put("真正姓名",20);
        snapshot("訂飲料","買早餐");new ContactsListener(factory).finish();assertEquals(Integer.valueOf(70),map("contacts").get("訂飲料"));
        GboardZhuyinDictionarySettings.writeEnabled(prefs(),false);
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.requestRefresh(context));awaitRefresh();
        new ContactsListener(factory).finish();assertEquals(map("realContacts"),map("contacts"));
        GboardZhuyinDictionarySettings.writeEnabled(prefs(),true);
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.requestRefresh(context));awaitRefresh();
        new ContactsListener(factory).finish();assertTrue(map("contacts").containsKey("買早餐"));
    }
    @Test public void failedDatabaseReadCannotReplacePublishedSnapshot()throws Exception{
        snapshot("訂飲料");long revision=prefs().getLong(GboardZhuyinPersonalDictionaryRuntime.REVISION,0);
        try(SQLiteDatabase db=context.openOrCreateDatabase("PersonalDictionary.db",0,null)){db.execSQL("DROP TABLE entry");}
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.requestRefresh(context));awaitRefresh();
        assertEquals(Collections.singleton("訂飲料"),prefs().getStringSet(GboardZhuyinPersonalDictionaryRuntime.WORDS,null));
        assertEquals(revision,prefs().getLong(GboardZhuyinPersonalDictionaryRuntime.REVISION,0));
        assertEquals(1,manager().getClass().getField("requests").getInt(manager()));
    }
    @Test public void failedContactsPersistenceDoesNotClaimSuccessfulRebuild()throws Exception{
        snapshot("訂飲料");factory.getClass().getField("persistFailure").setBoolean(factory,true);
        assertFalse(new ContactsListener(factory).finish()); assertTrue(map("contacts").isEmpty());
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
        assertEquals(-2,prefs().getInt(GboardZhuyinPersonalDictionaryRuntime.LAST_COUNT,0));
        factory.getClass().getField("persistFailure").setBoolean(factory,false);
        assertTrue(new ContactsListener(factory).finish());assertFalse(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
    }
    @Test public void abortedContactsTransactionCannotAcknowledgeTheNewDictionaryRevision()throws Exception{
        snapshot("訂飲料");ContactsListener listener=new ContactsListener(factory);
        GboardZhuyinPersonalDictionaryRuntime.mergeContacts(listener);
        GboardZhuyinPersonalDictionaryRuntime.contactsAborted(listener);
        listener.b.getClass().getMethod("close").invoke(listener.b);
        GboardZhuyinPersonalDictionaryRuntime.contactsFinished(factory);
        assertTrue(map("contacts").isEmpty());
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
    }
    @Test public void settingsShowSnapshotAndActualContactsImportCounts()throws Exception{
        snapshot("訂飲料");
        assertTrue(new GboardZhuyinDictionarySettingsFeature(context).getEntrySummary().contains("waiting"));
        new ContactsListener(factory).finish();
        String summary=new GboardZhuyinDictionarySettingsFeature(context).getEntrySummary();
        assertTrue(summary,summary.contains("Chinese dictionary words: 1"));
        assertTrue(summary,summary.contains("last rebuild: 2"));
    }
    @Test public void newerDictionaryRevisionStaysPendingIfOlderContactsBuildFinishesLater()throws Exception{
        snapshot("訂飲料");ContactsListener old=new ContactsListener(factory);
        GboardZhuyinPersonalDictionaryRuntime.mergeContacts(old);
        snapshot("買早餐");
        old.b.getClass().getMethod("b").invoke(old.b);old.b.getClass().getMethod("close").invoke(old.b);
        GboardZhuyinPersonalDictionaryRuntime.contactsFinished(factory);
        assertTrue(GboardZhuyinPersonalDictionaryRuntime.contactsRebuildRequired(manager()));
        new ContactsListener(factory).finish(); assertFalse(map("contacts").containsKey("訂飲料")); assertTrue(map("contacts").containsKey("買早餐"));
    }
    @Test public void removesOnlyOwnedUserDictionaryTermsFromPreviousPrototypeOnUpgrade()throws Exception{
        map("userWords").put("舊原型詞條",1);map("userWords").put("原有學習詞",600);
        prefs().edit().putStringSet(GboardZhuyinPersonalDictionaryRuntime.OWNED,Collections.singleton("舊原型詞條")).commit();
        snapshot("訂飲料"); new ContactsListener(factory).finish();
        assertEquals(Collections.singletonMap("原有學習詞",600),map("userWords"));assertTrue(map("contacts").containsKey("訂飲料"));
    }
    @Test public void unsupportedWordDoesNotPreventOtherWordsFromJoiningContacts()throws Exception{
        factory.getClass().getField("rejectedWord").set(factory,"罕見讀音");snapshot("罕見讀音","訂飲料");
        new ContactsListener(factory).finish();assertTrue(map("contacts").containsKey("訂飲料"));assertFalse(map("contacts").containsKey("罕見讀音"));
    }
}
