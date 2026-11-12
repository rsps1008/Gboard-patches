import android.content.Context;
/** The real manager invalidates its timestamp/count before scheduling the rebuild. */
public class ndd {
    public Context e;
    public int requests, invalidations;
    public static ndd instance;
    public static ndd w(Context context) {
        if(instance==null) {instance=new ndd();instance.e=context;} return instance;
    }
    public void A() {invalidations++;}
    public void p() {requests++;}
}
