import android.content.Context;
import java.util.*;
/** Fixture for the reviewed 18.0.3 native factory contract. */
public class hbd {
    public Context j;
    public Map<String, Integer> userWords = new LinkedHashMap<>();
    public Map<String, Integer> contacts = new LinkedHashMap<>();
    public Map<String, Integer> realContacts = new LinkedHashMap<>();
    public boolean persistFailure;
    public String rejectedWord;
    public int refreshed, closed, openedType;
    public void L(int type) { if (type != 2 && type != 3) throw new AssertionError(type); refreshed++; }
}
