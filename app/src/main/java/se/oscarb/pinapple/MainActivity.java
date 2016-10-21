package se.oscarb.pinapple;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableArrayList;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.query.Delete;

import se.oscarb.pinapple.databinding.ActivityMainBinding;


/*
 * TODO: Keep scroll position on rotation
 * TODO: Check "Analyze > Inspect Code..."
 *
 */

public class MainActivity extends AppCompatActivity implements AddCodeDialogFragment.AddCodeDialogListener {


    // Fields
    private static final String TAG = MainActivity.class.getSimpleName();

    private ObservableArrayList<Code> codeList;

    private CoordinatorLayout coordinatorLayout;

    private RecyclerView recyclerView;
    private RecyclerView.Adapter codeAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private int passcode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActiveAndroid.initialize(this);

        Log.d(TAG, "In the onCreate() event");

        // Started from PasscodeActivity?
        Intent fromPasscodeIntent = getIntent();
        passcode = fromPasscodeIntent.getIntExtra(PasscodeActivity.PASSCODE_MESSAGE, -1);

        // No passcode entered or not started from PasscodeActivity
        if (passcode == -1) {

            // Start PasscodeActivity
            Intent toPasscodeIntent = new Intent(this, PasscodeActivity.class);
            startActivity(toPasscodeIntent);
            finish();

        }

        setContentView(R.layout.activity_main);
        // Use data binding to toggle empty state
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        // Initialize fields
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator_layout);

        // Fill codeList
        codeList = new ObservableArrayList<>();
        codeList.addAll(Code.getAll()); // get from SQLite
        codeAdapter = new CodeAdapter(codeList, passcode);


        // Initialize RecyclerView
        recyclerView = (RecyclerView) findViewById(R.id.code_list_layout);
        recyclerView.setAdapter(codeAdapter);
        recyclerView.setHasFixedSize(true); // improves performance
        // Set LayoutManager
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(getResources().getInteger(R.integer.spanCount), StaggeredGridLayoutManager.VERTICAL));
        int columnGutterInPixels = getResources().getDimensionPixelSize(R.dimen.gutter);
        recyclerView.addItemDecoration(new CodeCardItemDecoration(columnGutterInPixels));


        // TODO: Add onItemLongClickListener for removal of codes?
        recyclerView.addOnItemTouchListener(new OnCodeTouchListener(this, recyclerView, new ClickListener() {
            @Override
            public void onClick(View view, int position) {
                Toast.makeText(MainActivity.this, "Single click " + position, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onLongClick(View view, int position) {
                Toast.makeText(MainActivity.this, "Long click " + position, Toast.LENGTH_SHORT).show();
            }
        }));

        // Add the App Bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Add the Floating Action Button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Open AddCodeDialogFragment
                DialogFragment addCodeDialog = new AddCodeDialogFragment();
                addCodeDialog.show(getSupportFragmentManager(), "addCodeDialog");
            }
        });

        // Bind codeList with UI
        binding.setCodeList(codeList);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (BuildConfig.DEBUG) {
            getMenuInflater().inflate(R.menu.menu_main_debug, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();


        if (id == R.id.action_clear_all && BuildConfig.DEBUG) {
            // Clear all codes
            int codeListSize = codeAdapter.getItemCount();
            new Delete().from(Code.class).execute();
            codeList.clear();
            codeAdapter.notifyItemRangeRemoved(0, codeListSize);

            return true;
        } else if (id == R.id.action_lock) {
            // Back to PasscodeActivity
            Intent toPasscodeActivityIntent = new Intent(this, PasscodeActivity.class);
            //toPasscodeActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(toPasscodeActivityIntent);
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    // Implemented from AddCodeDialogListener within AddCodeDialogFragment
    @Override
    public void onAddCodeDialogPositiveClick(DialogFragment dialog, String label, String codeText) {
        int codeListSize = codeAdapter.getItemCount();

        // Generate pattern from code
        String pattern = "";
        long codeValue = 0;
        long multiplier = 1;
        for (int i = codeText.length() - 1; i >= 0; i--) {
            if (Character.isDigit(codeText.charAt(i))) {
                codeValue += Character.getNumericValue(codeText.charAt(i)) * multiplier;
                multiplier *= 10;
                pattern = 'd' + pattern;
            } else {
                pattern = codeText.charAt(i) + pattern;
            }
        }

        // Encrypt code
        Crypto crypto = new XorCrypto();
        long encryptedValue = crypto.encrypt(codeValue, passcode);

        // Save to database
        Code code = new Code(label, encryptedValue, pattern);
        code.save();

        codeList.add(code);
        codeAdapter.notifyItemInserted(codeListSize);

        // Scroll to last card added
        recyclerView.scrollToPosition(codeAdapter.getItemCount() - 1);

        // Show undo action
        final Snackbar snackbar = Snackbar.make(coordinatorLayout, R.string.code_added, Snackbar.LENGTH_LONG);
        snackbar.setAction("Undo", new UndoClickListener());
        snackbar.show();

    }

    // Implemented from AddCodeDialogListener within AddCodeDialogFragment
    @Override
    public void onAddCodeDialogNegativeClick(DialogFragment dialog) {
        // No action
    }

    public static interface ClickListener {
        public void onClick(View view, int position);

        public void onLongClick(View view, int position);
    }

    class OnCodeTouchListener implements RecyclerView.OnItemTouchListener {

        private ClickListener clickListener;
        private GestureDetector gestureDetector;

        public OnCodeTouchListener(Context context, final RecyclerView recyclerView, final ClickListener clickListener) {
            this.clickListener = clickListener;
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null && clickListener != null) {
                        clickListener.onLongClick(child, recyclerView.getChildAdapterPosition(child));
                    }
                    super.onLongPress(e);
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
            View child = rv.findChildViewUnder(e.getX(), e.getY());
            if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)) {
                clickListener.onClick(child, rv.getChildAdapterPosition(child));
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {

        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

        }
    }

    // Clicking UNDO after code is added removes the latest code added
    private class UndoClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            int codeListSize = codeAdapter.getItemCount();
            Code code = codeList.remove(codeListSize - 1);
            code.delete();
            codeAdapter.notifyItemRemoved(codeListSize - 1);

            Snackbar.make(coordinatorLayout, R.string.code_removed, Snackbar.LENGTH_SHORT).show();
        }
    }
}
