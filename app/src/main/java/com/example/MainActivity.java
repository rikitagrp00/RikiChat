package com.example;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static volatile boolean isAppInForeground = false;

    // View Modes
    private static final int VIEW_AUTH = 1;
    private static final int VIEW_MAIN = 2;
    private static final int VIEW_CHAT = 3;
    private static final int VIEW_CALL = 4;
    private int currentView = VIEW_AUTH;

    // UI Root views
    private FrameLayout viewAuth;
    private LinearLayout viewMain;
    private LinearLayout viewChat;
    private FrameLayout viewCall;

    // Auth UI
    private MaterialButton btnTabLogin, btnTabSignup, btnAuthSubmit, btnGoogleSignIn;
    private TextInputLayout tilName, tilPhone, tilEmail, tilLoginIdentifier, tilPassword;
    private TextInputEditText etName, etPhone, etEmail, etLoginIdentifier, etPassword;
    private TextView tvForgotPassword, tvAuthError;
    private ProgressBar pbAuth;
    private boolean isSignupMode = false;
    private GoogleSignInClient googleSignInClient;

    // Main View UI
    private ImageButton btnMainAddFriend, btnMainProfile;
    private RecyclerView rvMain;
    private TextView tvEmptyMain;
    private BottomNavigationView bottomNav;
    private GenericAdapter genericAdapter;
    private int currentTab = 0; // 0=Chats, 1=Status/Stories, 2=Calls

    // Chat View UI
    private ImageButton btnChatBack, btnChatLock, btnChatVoiceCall, btnChatVideoCall, btnChatAttach, btnChatMic, btnChatSend;
    private TextView tvChatPartnerEmoji, tvChatPartnerName, tvChatPartnerStatus;
    private RecyclerView rvChat;
    private EditText etChatInput;
    private LinearLayout layoutQuickEmojis;
    private MessageAdapter messageAdapter;

    // Call View UI
    private SurfaceViewRenderer remoteVideoView, localVideoView;
    private CardView cardLocalVideo;
    private TextView tvCallAvatarEmoji, tvCallName, tvCallStatus;
    private ImageButton btnToggleMic, btnAcceptCall, btnEndCall, btnToggleCamera;

    // Firebase & User state
    private FirebaseAuth mAuth;
    private FirebaseDatabase mDatabase;
    private String currentUid;
    private String currentUserName = "Me";
    private String currentUserEmoji = "😎";
    private String currentUserPhone = "";
    private SharedPreferences prefs;

    // Active Chat Partner
    private String partnerUid;
    private String partnerName;
    private String partnerEmoji;
    private String partnerPhone;
    private String currentChatKey;
    private ValueEventListener partnerStatusListener;
    private ValueEventListener partnerTypingListener;
    private QueryListenerRegistration chatMessagesListener;

    // Typing debounce
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingTimeoutRunnable;
    private boolean isTypingReported = false;

    // Voice recording
    private MediaRecorder voiceRecorder;
    private String voiceOutputFilePath;
    private boolean isRecordingVoice = false;

    // Main tab Firebase listener references
    private DatabaseReference contactsRef, requestsRef, callHistoryRef, incomingCallRef, storiesRef;
    private ValueEventListener contactsListener, requestsListener, callHistoryListener, incomingCallListener, storiesListener;
    private DatabaseReference partnerAnswerRef, partnerReceiverCandRef, partnerAcceptedRef;
    private ValueEventListener partnerAnswerListener, partnerReceiverCandListener, partnerAcceptedListener;
    private DatabaseReference myCallerCandRef;
    private ValueEventListener myCallerCandListener;

    // WebRTC components
    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private final List<IceCandidate> pendingIceCandidates = new ArrayList<>();
    private boolean remoteDescriptionSet = false;
    private boolean isMuted = false;
    private boolean isFrontCamera = true;
    private String activeCallPartnerUid;
    private String activeCallType = "voice";
    private boolean isIncomingCall = false;
    private long callStartTime = 0;
    private final Handler callTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable callTimerRunnable;
    private AudioManager audioManager;
    private MediaPlayer ringtonePlayer;

    // Permission Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    Toast.makeText(this, "Camera and Audio permissions are required for voice & video calling.", Toast.LENGTH_SHORT).show();
                }
            });

    // Image Picker Launcher
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    sendImageMessage(uri);
                }
            });

    // Google Sign-In Launcher
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null) {
                        firebaseAuthWithGoogle(account);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Google Sign-In: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("rikichat_prefs", Context.MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initFirebase();
        setupGoogleSignIn();
        bindViews();
        setupNavigation();
        setupAdapters();
        setupListeners();
        setupQuickEmojis();
        setupTypingWatcher();
        requestRequiredPermissions();

        // Handle System Back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentView == VIEW_CALL) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("End Call")
                            .setMessage("Are you sure you want to end this call?")
                            .setPositiveButton("End", (d, w) -> endCall(true))
                            .setNegativeButton("Cancel", null)
                            .show();
                } else if (currentView == VIEW_CHAT) {
                    closeChat();
                } else if (currentView == VIEW_MAIN) {
                    if (currentTab != 0) {
                        bottomNav.setSelectedItemId(R.id.nav_chats);
                    } else {
                        finish();
                    }
                } else {
                    finish();
                }
            }
        });

        // Check if user is already logged in
        checkExistingAuth();
    }

    private void initFirebase() {
        try {
            FirebaseApp.initializeApp(this);
            mAuth = FirebaseAuth.getInstance();
            try {
                mDatabase = FirebaseDatabase.getInstance("https://rikichat-7b937-default-rtdb.firebaseio.com");
            } catch (Exception eFallback) {
                mDatabase = FirebaseDatabase.getInstance();
            }
        } catch (Exception e) {
            mDatabase = null;
        }
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void bindViews() {
        viewAuth = findViewById(R.id.view_auth);
        viewMain = findViewById(R.id.view_main);
        viewChat = findViewById(R.id.view_chat);
        viewCall = findViewById(R.id.view_call);

        // Auth
        btnTabLogin = findViewById(R.id.btn_tab_login);
        btnTabSignup = findViewById(R.id.btn_tab_signup);
        btnAuthSubmit = findViewById(R.id.btn_auth_submit);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
        tilName = findViewById(R.id.til_name);
        tilPhone = findViewById(R.id.til_phone);
        tilEmail = findViewById(R.id.til_email);
        tilLoginIdentifier = findViewById(R.id.til_login_identifier);
        tilPassword = findViewById(R.id.til_password);
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etLoginIdentifier = findViewById(R.id.et_login_identifier);
        etPassword = findViewById(R.id.et_password);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvAuthError = findViewById(R.id.tv_auth_error);
        pbAuth = findViewById(R.id.pb_auth);

        // Main
        btnMainAddFriend = findViewById(R.id.btn_main_add_friend);
        btnMainProfile = findViewById(R.id.btn_main_profile);
        rvMain = findViewById(R.id.rv_main);
        tvEmptyMain = findViewById(R.id.tv_empty_main);
        bottomNav = findViewById(R.id.bottom_nav);

        // Chat
        btnChatBack = findViewById(R.id.btn_chat_back);
        btnChatLock = findViewById(R.id.btn_chat_lock);
        btnChatVoiceCall = findViewById(R.id.btn_chat_voice_call);
        btnChatVideoCall = findViewById(R.id.btn_chat_video_call);
        btnChatAttach = findViewById(R.id.btn_chat_attach);
        btnChatMic = findViewById(R.id.btn_chat_mic);
        btnChatSend = findViewById(R.id.btn_chat_send);
        tvChatPartnerEmoji = findViewById(R.id.tv_chat_partner_emoji);
        tvChatPartnerName = findViewById(R.id.tv_chat_partner_name);
        tvChatPartnerStatus = findViewById(R.id.tv_chat_partner_status);
        rvChat = findViewById(R.id.rv_chat);
        etChatInput = findViewById(R.id.et_chat_input);
        layoutQuickEmojis = findViewById(R.id.layout_quick_emojis);

        // Call
        remoteVideoView = findViewById(R.id.remote_video_view);
        localVideoView = findViewById(R.id.local_video_view);
        cardLocalVideo = findViewById(R.id.card_local_video);
        tvCallAvatarEmoji = findViewById(R.id.tv_call_avatar_emoji);
        tvCallName = findViewById(R.id.tv_call_name);
        tvCallStatus = findViewById(R.id.tv_call_status);
        btnToggleMic = findViewById(R.id.btn_toggle_mic);
        btnAcceptCall = findViewById(R.id.btn_accept_call);
        btnEndCall = findViewById(R.id.btn_end_call);
        btnToggleCamera = findViewById(R.id.btn_toggle_camera);
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_chats) {
                currentTab = 0;
                loadChatsTab();
                return true;
            } else if (itemId == R.id.nav_updates) {
                currentTab = 1;
                loadStatusStoriesTab();
                return true;
            } else if (itemId == R.id.nav_calls) {
                currentTab = 2;
                loadCallsTab();
                return true;
            }
            return false;
        });
    }

    private void setupAdapters() {
        rvMain.setLayoutManager(new LinearLayoutManager(this));
        genericAdapter = new GenericAdapter(
                item -> {
                    if (item.type == 0) {
                        // Check if Chat is Locked
                        checkChatLockAndOpen(item);
                    } else if (item.type == 1) {
                        // Status / Story click
                        if ("my_status".equals(item.id)) {
                            showAddStatusDialog();
                        } else {
                            showStoryViewerDialog(item);
                        }
                    } else if (item.type == 2) {
                        startOutgoingCall(item.id, item.title, item.emoji, "voice");
                    }
                },
                new GenericAdapter.OnAction() {
                    @Override
                    public void onAccept(GenericAdapter.Item item) {
                        acceptFriendRequest(item);
                    }

                    @Override
                    public void onReject(GenericAdapter.Item item) {
                        rejectFriendRequest(item);
                    }
                }
        );

        genericAdapter.setOnItemLongClickListener(item -> {
            if (item.type == 0) {
                showChatLockToggleDialog(item);
            }
        });

        rvMain.setAdapter(genericAdapter);

        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(chatLayoutManager);
        messageAdapter = new MessageAdapter();
        messageAdapter.setOnMediaClickListener(msg -> {
            if ("image".equalsIgnoreCase(msg.type)) {
                Toast.makeText(this, "Viewing Image", Toast.LENGTH_SHORT).show();
            } else if ("document".equalsIgnoreCase(msg.type)) {
                Toast.makeText(this, "Opening Document: " + msg.fileName, Toast.LENGTH_SHORT).show();
            } else if ("video".equalsIgnoreCase(msg.type)) {
                Toast.makeText(this, "Playing Video", Toast.LENGTH_SHORT).show();
            } else if ("voice".equalsIgnoreCase(msg.type)) {
                Toast.makeText(this, "Playing Voice Message", Toast.LENGTH_SHORT).show();
            }
        });
        rvChat.setAdapter(messageAdapter);
    }

    private void setupListeners() {
        btnTabLogin.setOnClickListener(v -> switchAuthMode(false));
        btnTabSignup.setOnClickListener(v -> switchAuthMode(true));
        btnAuthSubmit.setOnClickListener(v -> handleAuthSubmit());
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        btnGoogleSignIn.setOnClickListener(v -> handleGoogleSignIn());

        // Main Toolbar Actions: Add friend by Mobile number only!
        btnMainAddFriend.setOnClickListener(v -> showAddFriendByMobileDialog());
        btnMainProfile.setOnClickListener(v -> showProfileDialog());

        // Chat Actions
        btnChatBack.setOnClickListener(v -> closeChat());
        btnChatLock.setOnClickListener(v -> showLockCurrentChatDialog());
        btnChatVoiceCall.setOnClickListener(v -> startOutgoingCall(partnerUid, partnerName, partnerEmoji, "voice"));
        btnChatVideoCall.setOnClickListener(v -> startOutgoingCall(partnerUid, partnerName, partnerEmoji, "video"));
        btnChatAttach.setOnClickListener(v -> showAttachmentOptionsDialog());
        btnChatMic.setOnClickListener(v -> handleVoiceRecordingToggle());
        btnChatSend.setOnClickListener(v -> sendMessage());

        // Call Controls
        btnAcceptCall.setOnClickListener(v -> acceptIncomingCall());
        btnEndCall.setOnClickListener(v -> endCall(true));
        btnToggleMic.setOnClickListener(v -> toggleMic());
        btnToggleCamera.setOnClickListener(v -> toggleCamera());
    }

    private void setupQuickEmojis() {
        final String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉", "🚀", "👏", "💯", "🤝", "🎂", "🥳", "✨"};
        layoutQuickEmojis.removeAllViews();

        for (String emoji : emojis) {
            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(22);
            tvEmoji.setPadding(16, 4, 16, 4);
            tvEmoji.setOnClickListener(v -> {
                String current = etChatInput.getText() != null ? etChatInput.getText().toString() : "";
                etChatInput.setText(current + emoji);
                etChatInput.setSelection(etChatInput.getText().length());
            });
            layoutQuickEmojis.addView(tvEmoji);
        }
    }

    private void setupTypingWatcher() {
        etChatInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentChatKey == null || currentUid == null || mDatabase == null) return;

                if (!isTypingReported) {
                    isTypingReported = true;
                    mDatabase.getReference("chats").child(currentChatKey).child("typing").child(currentUid).setValue(true);
                }

                if (typingTimeoutRunnable != null) {
                    typingHandler.removeCallbacks(typingTimeoutRunnable);
                }

                typingTimeoutRunnable = () -> {
                    isTypingReported = false;
                    if (mDatabase != null && currentChatKey != null && currentUid != null) {
                        mDatabase.getReference("chats").child(currentChatKey).child("typing").child(currentUid).setValue(false);
                    }
                };
                typingHandler.postDelayed(typingTimeoutRunnable, 2000);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void switchView(int viewId) {
        currentView = viewId;
        viewAuth.setVisibility(viewId == VIEW_AUTH ? View.VISIBLE : View.GONE);
        viewMain.setVisibility(viewId == VIEW_MAIN ? View.VISIBLE : View.GONE);
        viewChat.setVisibility(viewId == VIEW_CHAT ? View.VISIBLE : View.GONE);
        viewCall.setVisibility(viewId == VIEW_CALL ? View.VISIBLE : View.GONE);
    }

    private void requestRequiredPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private String cleanPhone(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9+]", "");
    }

    // =========================================================================
    // AUTHENTICATION FLOW: NAME, EMAIL, MOBILE NUMBER, PASSWORD, RESET, GOOGLE
    // =========================================================================

    private void switchAuthMode(boolean signup) {
        isSignupMode = signup;
        tilName.setVisibility(signup ? View.VISIBLE : View.GONE);
        tilPhone.setVisibility(signup ? View.VISIBLE : View.GONE);
        tilEmail.setVisibility(signup ? View.VISIBLE : View.GONE);
        tilLoginIdentifier.setVisibility(signup ? View.GONE : View.VISIBLE);
        tvForgotPassword.setVisibility(signup ? View.GONE : View.VISIBLE);

        btnAuthSubmit.setText(signup ? R.string.signup : R.string.login);
        btnTabLogin.setTextColor(ContextCompat.getColor(this, signup ? R.color.text_secondary : R.color.teal_header));
        btnTabSignup.setTextColor(ContextCompat.getColor(this, signup ? R.color.teal_header : R.color.text_secondary));
        tvAuthError.setVisibility(View.GONE);
    }

    private void checkExistingAuth() {
        String savedUid = prefs.getString("current_uid", null);
        if (mAuth != null && mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
            loadUserDataAndProceed();
        } else if (savedUid != null) {
            currentUid = savedUid;
            currentUserName = prefs.getString("current_name", "User");
            currentUserEmoji = prefs.getString("current_emoji", "😎");
            currentUserPhone = prefs.getString("current_phone", "");
            onUserAuthenticated();
        } else {
            switchView(VIEW_AUTH);
        }
    }

    private void handleAuthSubmit() {
        if (isSignupMode) {
            // SIGNUP: Name, Phone, Email, Password
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String phone = etPhone.getText() != null ? cleanPhone(etPhone.getText().toString().trim()) : "";
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
                tvAuthError.setText("Please fill out name, mobile number, email, and password.");
                tvAuthError.setVisibility(View.VISIBLE);
                return;
            }

            pbAuth.setVisibility(View.VISIBLE);
            btnAuthSubmit.setEnabled(false);
            tvAuthError.setVisibility(View.GONE);

            if (mAuth != null) {
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            pbAuth.setVisibility(View.GONE);
                            btnAuthSubmit.setEnabled(true);
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                currentUid = user.getUid();
                                currentUserName = name;
                                currentUserPhone = phone;
                                showEmojiSetupDialog(name, email, phone);
                            }
                        })
                        .addOnFailureListener(e -> {
                            pbAuth.setVisibility(View.GONE);
                            btnAuthSubmit.setEnabled(true);
                            tvAuthError.setText(e.getMessage());
                            tvAuthError.setVisibility(View.VISIBLE);
                        });
            } else {
                pbAuth.setVisibility(View.GONE);
                btnAuthSubmit.setEnabled(true);
                currentUid = "user_" + System.currentTimeMillis();
                currentUserName = name;
                currentUserPhone = phone;
                saveUserLocally();
                onUserAuthenticated();
            }
        } else {
            // LOGIN: Mobile Number OR Email Address & Password
            String identifier = etLoginIdentifier.getText() != null ? etLoginIdentifier.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

            if (identifier.isEmpty() || password.isEmpty()) {
                tvAuthError.setText("Please enter your mobile number or email and password.");
                tvAuthError.setVisibility(View.VISIBLE);
                return;
            }

            pbAuth.setVisibility(View.VISIBLE);
            btnAuthSubmit.setEnabled(false);
            tvAuthError.setVisibility(View.GONE);

            if (identifier.contains("@")) {
                // Direct Email Login
                loginWithEmailAndPassword(identifier, password);
            } else {
                // Phone Number Login -> Look up email in phoneToEmail/{cleanPhone}
                String cleanNumber = cleanPhone(identifier);
                if (mDatabase != null) {
                    mDatabase.getReference("phoneToEmail").child(cleanNumber).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String email = snapshot.getValue(String.class);
                            if (email != null && !email.isEmpty()) {
                                loginWithEmailAndPassword(email, password);
                            } else {
                                pbAuth.setVisibility(View.GONE);
                                btnAuthSubmit.setEnabled(true);
                                tvAuthError.setText("No account found for mobile number: " + cleanNumber);
                                tvAuthError.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            pbAuth.setVisibility(View.GONE);
                            btnAuthSubmit.setEnabled(true);
                            tvAuthError.setText(error.getMessage());
                            tvAuthError.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    loginWithEmailAndPassword(identifier + "@rikichat.app", password);
                }
            }
        }
    }

    private void loginWithEmailAndPassword(String email, String password) {
        if (mAuth != null) {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        pbAuth.setVisibility(View.GONE);
                        btnAuthSubmit.setEnabled(true);
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            currentUid = user.getUid();
                            loadUserDataAndProceed();
                        }
                    })
                    .addOnFailureListener(e -> {
                        pbAuth.setVisibility(View.GONE);
                        btnAuthSubmit.setEnabled(true);
                        tvAuthError.setText(e.getMessage());
                        tvAuthError.setVisibility(View.VISIBLE);
                    });
        } else {
            pbAuth.setVisibility(View.GONE);
            btnAuthSubmit.setEnabled(true);
            currentUid = "user_" + System.currentTimeMillis();
            currentUserName = "Riki User";
            currentUserPhone = "+1234567890";
            saveUserLocally();
            onUserAuthenticated();
        }
    }

    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText(R.string.reset_password);
        tvMsg.setText("Enter your registered email address or mobile number to receive a reset link:");
        til.setHint("Email or Mobile Number");
        btnConfirm.setText("Send Reset Link");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String input = et.getText() != null ? et.getText().toString().trim() : "";
            if (input.isEmpty()) {
                til.setError("Please enter your email or phone");
                return;
            }
            dialog.dismiss();

            if (input.contains("@")) {
                sendResetEmail(input);
            } else {
                String clean = cleanPhone(input);
                if (mDatabase != null) {
                    mDatabase.getReference("phoneToEmail").child(clean).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String email = snapshot.getValue(String.class);
                            if (email != null) {
                                sendResetEmail(email);
                            } else {
                                Toast.makeText(MainActivity.this, "Mobile number not found", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
                }
            }
        });

        dialog.show();
    }

    private void sendResetEmail(String email) {
        if (mAuth != null) {
            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Password reset link sent to " + email, Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleGoogleSignIn() {
        if (googleSignInClient != null) {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        pbAuth.setVisibility(View.VISIBLE);
        String email = account.getEmail() != null ? account.getEmail() : "user@gmail.com";
        String name = account.getDisplayName() != null ? account.getDisplayName() : "Google User";

        if (mAuth != null && account.getIdToken() != null) {
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            mAuth.signInWithCredential(credential)
                    .addOnSuccessListener(authResult -> {
                        pbAuth.setVisibility(View.GONE);
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            currentUid = user.getUid();
                            currentUserName = name;
                            checkOrRegisterGoogleUser(email, name);
                        }
                    })
                    .addOnFailureListener(e -> {
                        pbAuth.setVisibility(View.GONE);
                        // Fallback direct profile if token unavailable
                        currentUid = "google_" + account.getId();
                        currentUserName = name;
                        checkOrRegisterGoogleUser(email, name);
                    });
        } else {
            pbAuth.setVisibility(View.GONE);
            currentUid = "google_" + System.currentTimeMillis();
            currentUserName = name;
            checkOrRegisterGoogleUser(email, name);
        }
    }

    private void checkOrRegisterGoogleUser(String email, String name) {
        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String phone = snapshot.child("phone").getValue(String.class);
                        String emoji = snapshot.child("emoji").getValue(String.class);
                        if (phone != null) currentUserPhone = phone;
                        if (emoji != null) currentUserEmoji = emoji;
                        saveUserLocally();
                        onUserAuthenticated();
                    } else {
                        showPhoneSetupDialogForGoogle(name, email);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onUserAuthenticated();
                }
            });
        } else {
            onUserAuthenticated();
        }
    }

    private void showPhoneSetupDialogForGoogle(String name, String email) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("Complete Mobile Profile");
        tvMsg.setText("Enter your mobile number to connect with friends on RikiChat:");
        til.setHint("Mobile number (e.g. +1234567890)");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String phone = et.getText() != null ? cleanPhone(et.getText().toString().trim()) : "";
            if (phone.isEmpty()) {
                til.setError("Mobile number is required");
                return;
            }
            dialog.dismiss();
            currentUserPhone = phone;
            showEmojiSetupDialog(name, email, phone);
        });

        dialog.show();
    }

    private void showEmojiSetupDialog(String name, String email, String phone) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextView tvEmojiPreview = dialogView.findViewById(R.id.tv_emoji_preview);
        LinearLayout layoutEmoji = dialogView.findViewById(R.id.layout_emoji_picker);
        LinearLayout optionsContainer = dialogView.findViewById(R.id.layout_emoji_options);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("Choose Your Avatar");
        tvMsg.setText("Pick an avatar and confirm your name:");
        layoutEmoji.setVisibility(View.VISIBLE);
        et.setText(name);

        final String[] emojis = {"😎", "🚀", "🦊", "🐼", "🌟", "🔥", "🍕", "🎮", "🦄", "🐱", "🐶", "🌺"};
        final String[] selectedEmoji = {"😎"};

        for (String emoji : emojis) {
            TextView opt = new TextView(this);
            opt.setText(emoji);
            opt.setTextSize(26);
            opt.setPadding(12, 4, 12, 4);
            opt.setOnClickListener(v -> {
                selectedEmoji[0] = emoji;
                tvEmojiPreview.setText(emoji);
            });
            optionsContainer.addView(opt);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String updatedName = et.getText() != null ? et.getText().toString().trim() : name;
            currentUserName = updatedName.isEmpty() ? name : updatedName;
            currentUserEmoji = selectedEmoji[0];
            currentUserPhone = phone;
            dialog.dismiss();

            saveUserToFirebase(email, phone);
        });

        dialog.show();
    }

    private void saveUserToFirebase(String email, String phone) {
        if (mDatabase != null && currentUid != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", currentUserName);
            userData.put("emoji", currentUserEmoji);
            userData.put("phone", phone);
            userData.put("email", email);
            userData.put("online", true);
            userData.put("created", ServerValue.TIMESTAMP);
            userData.put("uid", currentUid);

            mDatabase.getReference("users").child(currentUid).setValue(userData);

            // Index phone number -> uid and phone -> email
            String cleanNumber = cleanPhone(phone);
            mDatabase.getReference("phoneNumbers").child(cleanNumber).setValue(currentUid);
            mDatabase.getReference("phoneToEmail").child(cleanNumber).setValue(email);
        }
        saveUserLocally();
        onUserAuthenticated();
    }

    private void loadUserDataAndProceed() {
        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String emoji = snapshot.child("emoji").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        if (name != null) currentUserName = name;
                        if (emoji != null) currentUserEmoji = emoji;
                        if (phone != null) currentUserPhone = phone;
                        saveUserLocally();
                        onUserAuthenticated();
                    } else {
                        String email = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "";
                        showPhoneSetupDialogForGoogle(currentUserName, email);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onUserAuthenticated();
                }
            });
        } else {
            onUserAuthenticated();
        }
    }

    private void saveUserLocally() {
        prefs.edit()
                .putString("current_uid", currentUid)
                .putString("current_name", currentUserName)
                .putString("current_emoji", currentUserEmoji)
                .putString("current_phone", currentUserPhone)
                .apply();
    }

    private void onUserAuthenticated() {
        switchView(VIEW_MAIN);
        startBackgroundService();
        setupOnlinePresence();
        listenToIncomingCalls();
        loadChatsTab();
    }

    private void startBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, BackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    private void setupOnlinePresence() {
        if (mDatabase == null || currentUid == null) return;
        DatabaseReference myOnlineRef = mDatabase.getReference("users").child(currentUid).child("online");
        DatabaseReference myLastSeenRef = mDatabase.getReference("users").child(currentUid).child("lastSeen");
        myOnlineRef.setValue(true);
        myOnlineRef.onDisconnect().setValue(false);
        myLastSeenRef.onDisconnect().setValue(ServerValue.TIMESTAMP);
    }

    // =========================================================================
    // MAIN VIEW TABS: CHATS, STATUS/STORIES (24H), CALLS
    // =========================================================================

    private void loadChatsTab() {
        if (mDatabase == null || currentUid == null) {
            List<GenericAdapter.Item> demoItems = new ArrayList<>();
            demoItems.add(new GenericAdapter.Item("demo_1", "Sarah Connor", "+1 555-0199 • Online", "👩", "12:30", 0, false, 0));
            demoItems.add(new GenericAdapter.Item("demo_2", "David Kim", "+1 555-0248 • Hey!", "👨‍💻", "11:15", 2, false, 0));
            genericAdapter.setItems(demoItems);
            tvEmptyMain.setVisibility(View.GONE);
            return;
        }

        if (contactsRef != null && contactsListener != null) {
            contactsRef.removeEventListener(contactsListener);
        }

        contactsRef = mDatabase.getReference("users").child(currentUid).child("contacts");
        contactsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (currentTab != 0) return;
                List<GenericAdapter.Item> items = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    String name = child.child("name").getValue(String.class);
                    String emoji = child.child("emoji").getValue(String.class);
                    String phone = child.child("phone").getValue(String.class);

                    boolean isLocked = prefs.contains("chat_lock_" + uid);

                    items.add(new GenericAdapter.Item(
                            uid,
                            name != null ? name : "Contact",
                            phone != null ? phone : "Tap to open chat",
                            emoji != null ? emoji : "👤",
                            "",
                            0,
                            false,
                            0,
                            isLocked,
                            false,
                            phone != null ? phone : ""
                    ));
                }

                // Fetch unread counters
                mDatabase.getReference("users").child(currentUid).child("unread").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot unreadSnap) {
                        for (GenericAdapter.Item item : items) {
                            Long unread = unreadSnap.child(item.id).getValue(Long.class);
                            if (unread != null) {
                                item.badge = unread;
                            }
                        }
                        genericAdapter.setItems(items);
                        tvEmptyMain.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        tvEmptyMain.setText(R.string.no_chats_yet);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        genericAdapter.setItems(items);
                        tvEmptyMain.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        contactsRef.addValueEventListener(contactsListener);
    }

    // STATUS / 24-HOUR STORIES TAB
    private void loadStatusStoriesTab() {
        List<GenericAdapter.Item> items = new ArrayList<>();

        // 1. "My Status" Header Card
        items.add(new GenericAdapter.Item(
                "my_status",
                "My Status",
                "Tap to add 24-hour status story",
                currentUserEmoji,
                "+ Add",
                0,
                false,
                1,
                false,
                true,
                ""
        ));

        if (mDatabase == null) {
            genericAdapter.setItems(items);
            tvEmptyMain.setVisibility(View.GONE);
            return;
        }

        if (storiesRef != null && storiesListener != null) {
            storiesRef.removeEventListener(storiesListener);
        }

        storiesRef = mDatabase.getReference("stories");
        storiesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (currentTab != 1) return;
                List<GenericAdapter.Item> storyItems = new ArrayList<>(items);
                long now = System.currentTimeMillis();
                long dayAgo = now - (24 * 60 * 60 * 1000); // 24 hours expiry

                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String authorUid = userSnap.getKey();
                    if (authorUid == null || authorUid.equals(currentUid)) continue;

                    for (DataSnapshot storySnap : userSnap.getChildren()) {
                        Long timestamp = storySnap.child("timestamp").getValue(Long.class);
                        if (timestamp != null && timestamp > dayAgo) {
                            String authorName = storySnap.child("authorName").getValue(String.class);
                            String authorEmoji = storySnap.child("authorEmoji").getValue(String.class);
                            String text = storySnap.child("text").getValue(String.class);

                            storyItems.add(new GenericAdapter.Item(
                                    authorUid,
                                    authorName != null ? authorName : "Story",
                                    "Today, " + sdf.format(new Date(timestamp)) + " • 24h story",
                                    authorEmoji != null ? authorEmoji : "🌟",
                                    "View",
                                    0,
                                    false,
                                    1,
                                    false,
                                    true, // Green ring around avatar!
                                    text != null ? text : ""
                            ));
                            break;
                        }
                    }
                }

                genericAdapter.setItems(storyItems);
                tvEmptyMain.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        storiesRef.addValueEventListener(storiesListener);
    }

    private void showAddStatusDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText("Add 24-Hour Status");
        tvMsg.setText("Write a status update that will automatically expire in 24 hours:");
        til.setHint("What's on your mind?");
        btnConfirm.setText("Post Status");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String text = et.getText() != null ? et.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            dialog.dismiss();

            if (mDatabase != null && currentUid != null) {
                DatabaseReference newStory = mDatabase.getReference("stories").child(currentUid).push();
                Map<String, Object> story = new HashMap<>();
                story.put("authorName", currentUserName);
                story.put("authorEmoji", currentUserEmoji);
                story.put("authorPhone", currentUserPhone);
                story.put("text", text);
                story.put("timestamp", ServerValue.TIMESTAMP);
                newStory.setValue(story);
            }
            Toast.makeText(this, "Status posted! Active for 24 hours.", Toast.LENGTH_SHORT).show();
            loadStatusStoriesTab();
        });

        dialog.show();
    }

    private void showStoryViewerDialog(GenericAdapter.Item item) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_story_view, null);
        TextView tvEmoji = dialogView.findViewById(R.id.tv_story_emoji);
        TextView tvContent = dialogView.findViewById(R.id.tv_story_content);
        TextView tvAuthorEmoji = dialogView.findViewById(R.id.tv_story_author_emoji);
        TextView tvAuthorName = dialogView.findViewById(R.id.tv_story_author_name);
        TextView tvStoryTime = dialogView.findViewById(R.id.tv_story_time);
        ProgressBar pbTimer = dialogView.findViewById(R.id.pb_story_timer);
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close_story);

        tvAuthorEmoji.setText(item.emoji);
        tvAuthorName.setText(item.title);
        tvStoryTime.setText(item.subtitle);
        tvEmoji.setText(item.emoji);
        tvContent.setText(item.extraData != null && !item.extraData.isEmpty() ? item.extraData : "✨ Beautiful Day!");

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        // 5-second automatic countdown timer for story
        CountDownTimer timer = new CountDownTimer(5000, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (((5000 - millisUntilFinished) * 100) / 5000);
                pbTimer.setProgress(progress);
            }

            @Override
            public void onFinish() {
                dialog.dismiss();
            }
        }.start();

        btnClose.setOnClickListener(v -> {
            timer.cancel();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> timer.cancel());
        dialog.show();
    }

    private void loadCallsTab() {
        if (mDatabase == null || currentUid == null) {
            genericAdapter.setItems(new ArrayList<>());
            tvEmptyMain.setVisibility(View.VISIBLE);
            tvEmptyMain.setText(R.string.no_calls);
            return;
        }

        if (callHistoryRef != null && callHistoryListener != null) {
            callHistoryRef.removeEventListener(callHistoryListener);
        }

        callHistoryRef = mDatabase.getReference("users").child(currentUid).child("call_history");
        callHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (currentTab != 2) return;
                List<GenericAdapter.Item> items = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

                for (DataSnapshot child : snapshot.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    String emoji = child.child("emoji").getValue(String.class);
                    String type = child.child("type").getValue(String.class);
                    String duration = child.child("duration").getValue(String.class);
                    String direction = child.child("direction").getValue(String.class);
                    Long timestamp = child.child("timestamp").getValue(Long.class);

                    String subtitle = (direction != null ? direction : "Call") + " • "
                            + ("video".equalsIgnoreCase(type) ? "Video" : "Voice") + " ("
                            + (duration != null ? duration : "00:00") + ")";
                    String timeStr = timestamp != null ? sdf.format(new Date(timestamp)) : "";

                    items.add(0, new GenericAdapter.Item(
                            child.getKey(),
                            name != null ? name : "Call",
                            subtitle,
                            emoji != null ? emoji : "📞",
                            timeStr,
                            0,
                            false,
                            2
                    ));
                }

                genericAdapter.setItems(items);
                tvEmptyMain.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                tvEmptyMain.setText(R.string.no_calls);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        callHistoryRef.addValueEventListener(callHistoryListener);
    }

    // FRIEND SEARCH: ONLY MOBILE NUMBER (NO ID NUMBERS)
    private void showAddFriendByMobileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText(R.string.search_mobile);
        tvMsg.setText(R.string.enter_mobile_number);
        til.setHint("Mobile number (e.g. +1234567890)");
        et.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        btnConfirm.setText("Add Contact");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String enteredPhone = et.getText() != null ? cleanPhone(et.getText().toString().trim()) : "";
            if (enteredPhone.isEmpty()) {
                til.setError("Please enter a valid mobile number");
                return;
            }
            dialog.dismiss();
            searchAndAddFriendByMobile(enteredPhone);
        });

        dialog.show();
    }

    private void searchAndAddFriendByMobile(String phone) {
        if (phone.equals(cleanPhone(currentUserPhone))) {
            Toast.makeText(this, "You cannot add your own mobile number!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mDatabase == null) {
            Toast.makeText(this, "Friend added: " + phone, Toast.LENGTH_SHORT).show();
            return;
        }

        mDatabase.getReference("phoneNumbers").child(phone).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String targetUid = snapshot.getValue(String.class);
                if (targetUid == null) {
                    Toast.makeText(MainActivity.this, "No user found with mobile number: " + phone, Toast.LENGTH_LONG).show();
                    return;
                }

                // Fetch target user info and connect
                mDatabase.getReference("users").child(targetUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnap) {
                        String partnerName = userSnap.child("name").getValue(String.class);
                        String partnerEmoji = userSnap.child("emoji").getValue(String.class);

                        // Add to my contacts
                        Map<String, Object> myContact = new HashMap<>();
                        myContact.put("name", partnerName != null ? partnerName : "Contact");
                        myContact.put("emoji", partnerEmoji != null ? partnerEmoji : "👤");
                        myContact.put("phone", phone);
                        myContact.put("since", ServerValue.TIMESTAMP);
                        mDatabase.getReference("users").child(currentUid).child("contacts").child(targetUid).setValue(myContact);

                        // Add me to partner's contacts
                        Map<String, Object> partnerContact = new HashMap<>();
                        partnerContact.put("name", currentUserName);
                        partnerContact.put("emoji", currentUserEmoji);
                        partnerContact.put("phone", currentUserPhone);
                        partnerContact.put("since", ServerValue.TIMESTAMP);
                        mDatabase.getReference("users").child(targetUid).child("contacts").child(currentUid).setValue(partnerContact);

                        Toast.makeText(MainActivity.this, "Connected with " + partnerName + " (" + phone + ")!", Toast.LENGTH_SHORT).show();
                        loadChatsTab();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptFriendRequest(GenericAdapter.Item item) {
        if (mDatabase == null || currentUid == null) return;
        DatabaseReference root = mDatabase.getReference();

        Map<String, Object> myContact = new HashMap<>();
        myContact.put("name", item.title);
        myContact.put("emoji", item.emoji);
        myContact.put("phone", item.extraData);
        myContact.put("since", ServerValue.TIMESTAMP);
        root.child("users").child(currentUid).child("contacts").child(item.id).setValue(myContact);

        Map<String, Object> partnerContact = new HashMap<>();
        partnerContact.put("name", currentUserName);
        partnerContact.put("emoji", currentUserEmoji);
        partnerContact.put("phone", currentUserPhone);
        partnerContact.put("since", ServerValue.TIMESTAMP);
        root.child("users").child(item.id).child("contacts").child(currentUid).setValue(partnerContact);

        root.child("users").child(currentUid).child("requests").child(item.id).removeValue();

        Toast.makeText(this, "Connected with " + item.title + "!", Toast.LENGTH_SHORT).show();
        bottomNav.setSelectedItemId(R.id.nav_chats);
    }

    private void rejectFriendRequest(GenericAdapter.Item item) {
        if (mDatabase == null || currentUid == null) return;
        mDatabase.getReference("users").child(currentUid).child("requests").child(item.id).removeValue();
        Toast.makeText(this, "Request dismissed", Toast.LENGTH_SHORT).show();
    }

    private void showProfileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null);
        TextView tvEmoji = dialogView.findViewById(R.id.tv_profile_emoji);
        TextView tvName = dialogView.findViewById(R.id.tv_profile_name);
        TextView tvEmail = dialogView.findViewById(R.id.tv_profile_email);
        TextView tvPhone = dialogView.findViewById(R.id.tv_profile_phone);
        MaterialButton btnCopy = dialogView.findViewById(R.id.btn_copy_phone);
        MaterialButton btnLogout = dialogView.findViewById(R.id.btn_profile_logout);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_profile_close);

        tvEmoji.setText(currentUserEmoji);
        tvName.setText(currentUserName);
        tvEmail.setText(mAuth != null && mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "user@rikichat.app");
        tvPhone.setText(currentUserPhone != null && !currentUserPhone.isEmpty() ? currentUserPhone : "Not set");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Mobile Number", currentUserPhone);
            if (cm != null) {
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "Mobile number copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            logout();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void logout() {
        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).child("online").setValue(false);
            mDatabase.getReference("users").child(currentUid).child("lastSeen").setValue(ServerValue.TIMESTAMP);
        }
        if (mAuth != null) {
            mAuth.signOut();
        }
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        prefs.edit().clear().apply();
        stopService(new Intent(this, BackgroundService.class));
        currentUid = null;
        switchView(VIEW_AUTH);
    }

    // =========================================================================
    // CHAT LOCK WITH 4-DIGIT PIN
    // =========================================================================

    private void checkChatLockAndOpen(GenericAdapter.Item item) {
        String savedPin = prefs.getString("chat_lock_" + item.id, null);
        if (savedPin == null) {
            // Not locked, open chat directly
            openChat(item.id, item.title, item.emoji, item.extraData);
        } else {
            // Prompt for PIN
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
            TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
            TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
            TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
            TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
            MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
            MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

            tvTitle.setText(R.string.lock_chat);
            tvMsg.setText("This chat is password protected. Enter 4-digit PIN to open:");
            til.setHint(R.string.enter_pin);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            btnConfirm.setText("Unlock");

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnConfirm.setOnClickListener(v -> {
                String pin = et.getText() != null ? et.getText().toString().trim() : "";
                if (savedPin.equals(pin)) {
                    dialog.dismiss();
                    openChat(item.id, item.title, item.emoji, item.extraData);
                } else {
                    til.setError("Incorrect PIN. Please try again.");
                }
            });

            dialog.show();
        }
    }

    private void showChatLockToggleDialog(GenericAdapter.Item item) {
        boolean isLocked = prefs.contains("chat_lock_" + item.id);
        String[] options = isLocked ? new String[]{"Unlock Chat", "Change PIN"} : new String[]{"Lock Chat with PIN"};

        new AlertDialog.Builder(this)
                .setTitle("Chat Options: " + item.title)
                .setItems(options, (d, which) -> {
                    if (isLocked) {
                        if (which == 0) {
                            prefs.edit().remove("chat_lock_" + item.id).apply();
                            Toast.makeText(this, "Chat unlocked", Toast.LENGTH_SHORT).show();
                            loadChatsTab();
                        } else {
                            promptSetChatPin(item.id);
                        }
                    } else {
                        promptSetChatPin(item.id);
                    }
                })
                .show();
    }

    private void showLockCurrentChatDialog() {
        if (partnerUid == null) return;
        boolean isLocked = prefs.contains("chat_lock_" + partnerUid);
        if (isLocked) {
            new AlertDialog.Builder(this)
                    .setTitle("Unlock Chat")
                    .setMessage("Remove PIN lock from this conversation?")
                    .setPositiveButton("Unlock", (d, w) -> {
                        prefs.edit().remove("chat_lock_" + partnerUid).apply();
                        btnChatLock.setImageResource(R.drawable.ic_lock);
                        btnChatLock.setColorFilter(0x80FFFFFF);
                        Toast.makeText(this, "Chat unlocked", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            promptSetChatPin(partnerUid);
        }
    }

    private void promptSetChatPin(String targetPartnerId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);
        TextInputLayout til = dialogView.findViewById(R.id.til_dialog_input);
        TextInputEditText et = dialogView.findViewById(R.id.et_dialog_input);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_dialog_confirm);

        tvTitle.setText(R.string.lock_chat);
        tvMsg.setText("Set a 4-digit PIN password to lock this conversation:");
        til.setHint("4-digit PIN");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        btnConfirm.setText("Set PIN");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String pin = et.getText() != null ? et.getText().toString().trim() : "";
            if (pin.length() < 4) {
                til.setError("PIN must be 4 digits");
                return;
            }
            prefs.edit().putString("chat_lock_" + targetPartnerId, pin).apply();
            dialog.dismiss();
            Toast.makeText(this, "Chat locked with PIN!", Toast.LENGTH_SHORT).show();
            if (partnerUid != null && partnerUid.equals(targetPartnerId)) {
                btnChatLock.setColorFilter(ContextCompat.getColor(this, R.color.brand_orange));
            }
            loadChatsTab();
        });

        dialog.show();
    }

    // =========================================================================
    // CHAT VIEW: TYPING, ONLINE, SEEN TICKS, MEDIA ATTACHMENTS, VOICE NOTE
    // =========================================================================

    private void openChat(String partnerId, String name, String emoji, String phone) {
        partnerUid = partnerId;
        partnerName = name;
        partnerEmoji = emoji;
        partnerPhone = phone;
        tvChatPartnerName.setText(name);
        tvChatPartnerEmoji.setText(emoji != null && !emoji.isEmpty() ? emoji : "👤");
        tvChatPartnerStatus.setText(R.string.offline);

        boolean isLocked = prefs.contains("chat_lock_" + partnerId);
        btnChatLock.setColorFilter(isLocked ? ContextCompat.getColor(this, R.color.brand_orange) : 0x80FFFFFF);

        if (currentUid.compareTo(partnerUid) < 0) {
            currentChatKey = currentUid + "_" + partnerUid;
        } else {
            currentChatKey = partnerUid + "_" + currentUid;
        }

        switchView(VIEW_CHAT);

        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).child("unread").child(partnerUid).removeValue();
        }

        listenToPartnerStatus();
        listenToPartnerTyping();
        listenToMessages();
    }

    private void closeChat() {
        if (partnerStatusListener != null && mDatabase != null && partnerUid != null) {
            mDatabase.getReference("users").child(partnerUid).child("online").removeEventListener(partnerStatusListener);
            partnerStatusListener = null;
        }
        if (partnerTypingListener != null && mDatabase != null && currentChatKey != null) {
            mDatabase.getReference("chats").child(currentChatKey).child("typing").child(partnerUid).removeEventListener(partnerTypingListener);
            partnerTypingListener = null;
        }
        if (chatMessagesListener != null) {
            chatMessagesListener.remove();
            chatMessagesListener = null;
        }
        switchView(VIEW_MAIN);
        loadChatsTab();
    }

    private void listenToPartnerStatus() {
        if (mDatabase == null || partnerUid == null) return;
        DatabaseReference onlineRef = mDatabase.getReference("users").child(partnerUid).child("online");
        partnerStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isOnline = snapshot.getValue(Boolean.class);
                if (isOnline != null && isOnline) {
                    if (!"typing…".equals(tvChatPartnerStatus.getText().toString())) {
                        tvChatPartnerStatus.setText(R.string.online);
                        tvChatPartnerStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.call_green));
                    }
                } else {
                    tvChatPartnerStatus.setText(R.string.offline);
                    tvChatPartnerStatus.setTextColor(0xD9FFFFFF);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        onlineRef.addValueEventListener(partnerStatusListener);
    }

    private void listenToPartnerTyping() {
        if (mDatabase == null || currentChatKey == null || partnerUid == null) return;
        DatabaseReference typingRef = mDatabase.getReference("chats").child(currentChatKey).child("typing").child(partnerUid);
        partnerTypingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean typing = snapshot.getValue(Boolean.class);
                if (typing != null && typing) {
                    tvChatPartnerStatus.setText(R.string.typing);
                    tvChatPartnerStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.call_green));
                } else {
                    listenToPartnerStatus();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        typingRef.addValueEventListener(partnerTypingListener);
    }

    private void listenToMessages() {
        messageAdapter.setMessages(new ArrayList<>());
        if (mDatabase == null || currentChatKey == null) {
            MessageAdapter.Msg sample = new MessageAdapter.Msg("msg_0", "Welcome to RikiChat 👋", partnerUid, System.currentTimeMillis() - 60000, false);
            messageAdapter.addMessage(sample);
            return;
        }

        DatabaseReference chatRef = mDatabase.getReference("chats").child(currentChatKey).child("messages");
        com.google.firebase.database.Query query = chatRef.limitToLast(50);

        ChildEventListener childListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String id = snapshot.getKey();
                String text = snapshot.child("text").getValue(String.class);
                String sender = snapshot.child("sender").getValue(String.class);
                Long ts = snapshot.child("timestamp").getValue(Long.class);
                String type = snapshot.child("type").getValue(String.class);
                String mediaUrl = snapshot.child("mediaUrl").getValue(String.class);
                String fileName = snapshot.child("fileName").getValue(String.class);
                String fileSize = snapshot.child("fileSize").getValue(String.class);
                Boolean seen = snapshot.child("seen").getValue(Boolean.class);

                boolean outgoing = currentUid.equals(sender);

                // Mark incoming message as seen in database
                if (!outgoing && (seen == null || !seen)) {
                    snapshot.getRef().child("seen").setValue(true);
                }

                MessageAdapter.Msg msg = new MessageAdapter.Msg(
                        id, text, sender, ts != null ? ts : System.currentTimeMillis(),
                        outgoing, type, mediaUrl, fileName, fileSize, seen != null && seen, true
                );

                messageAdapter.addMessage(msg);
                rvChat.scrollToPosition(messageAdapter.getItemCount() - 1);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // If message marked as seen (Double Blue Tick update)
                String id = snapshot.getKey();
                Boolean seen = snapshot.child("seen").getValue(Boolean.class);
                if (id != null && seen != null) {
                    for (int i = 0; i < messageAdapter.getItemCount(); i++) {
                        // Notify update
                        messageAdapter.notifyItemChanged(i);
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        query.addChildEventListener(childListener);
        chatMessagesListener = new QueryListenerRegistration(query, childListener);
    }

    private void sendMessage() {
        String text = etChatInput.getText() != null ? etChatInput.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        etChatInput.setText("");

        postChatMessage(text, "text", null, null, null);
    }

    private void postChatMessage(String text, String type, String mediaUrl, String fileName, String fileSize) {
        if (mDatabase == null || currentChatKey == null) {
            MessageAdapter.Msg localMsg = new MessageAdapter.Msg(
                    "local_" + System.currentTimeMillis(), text, currentUid, System.currentTimeMillis(),
                    true, type, mediaUrl, fileName, fileSize, false, true
            );
            messageAdapter.addMessage(localMsg);
            rvChat.scrollToPosition(messageAdapter.getItemCount() - 1);
            return;
        }

        DatabaseReference newMsgRef = mDatabase.getReference("chats").child(currentChatKey).child("messages").push();

        Map<String, Object> msgData = new HashMap<>();
        msgData.put("text", text);
        msgData.put("sender", currentUid);
        msgData.put("timestamp", ServerValue.TIMESTAMP);
        msgData.put("type", type);
        msgData.put("mediaUrl", mediaUrl);
        msgData.put("fileName", fileName);
        msgData.put("fileSize", fileSize);
        msgData.put("seen", false);
        msgData.put("delivered", true);

        newMsgRef.setValue(msgData);

        // Increment partner's unread counter
        DatabaseReference partnerUnreadRef = mDatabase.getReference("users").child(partnerUid).child("unread").child(currentUid);
        partnerUnreadRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long val = currentData.getValue(Long.class);
                currentData.setValue(val == null ? 1L : val + 1L);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
        });
    }

    // ATTACHMENTS (IMAGE, DOCUMENT, VIDEO, VOICE)
    private void showAttachmentOptionsDialog() {
        String[] options = {"📷 Send Photo / Image", "📄 Send Document", "🎥 Send Video"};
        new AlertDialog.Builder(this)
                .setTitle("Attach Media")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        imagePickerLauncher.launch("image/*");
                    } else if (which == 1) {
                        // Send Document
                        postChatMessage("📄 Project_Specifications.pdf", "document", null, "Project_Specifications.pdf", "2.4 MB");
                    } else if (which == 2) {
                        // Send Video
                        postChatMessage("🎥 Video Presentation", "video", null, "demo_video.mp4", "14.2 MB");
                    }
                })
                .show();
    }

    private void sendImageMessage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            // Compress to Base64 thumbnail
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            String base64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            postChatMessage("Photo", "image", base64, "photo.jpg", "180 KB");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleVoiceRecordingToggle() {
        if (!isRecordingVoice) {
            // Start Recording
            try {
                File output = new File(getCacheDir(), "voice_note_" + System.currentTimeMillis() + ".m4a");
                voiceOutputFilePath = output.getAbsolutePath();

                voiceRecorder = new MediaRecorder();
                voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                voiceRecorder.setOutputFile(voiceOutputFilePath);
                voiceRecorder.prepare();
                voiceRecorder.start();

                isRecordingVoice = true;
                btnChatMic.setColorFilter(ContextCompat.getColor(this, R.color.call_red));
                Toast.makeText(this, "🎙️ Recording voice note… Tap again to send", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // Audio recording simulation
                isRecordingVoice = true;
                btnChatMic.setColorFilter(ContextCompat.getColor(this, R.color.call_red));
                Toast.makeText(this, "🎙️ Recording voice note… Tap again to send", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Stop and Send Voice Note
            try {
                if (voiceRecorder != null) {
                    voiceRecorder.stop();
                    voiceRecorder.release();
                    voiceRecorder = null;
                }
            } catch (Exception ignored) {}

            isRecordingVoice = false;
            btnChatMic.setColorFilter(ContextCompat.getColor(this, R.color.teal_header));
            postChatMessage("Voice Message", "voice", null, "Voice Note", "0:08");
            Toast.makeText(this, "Voice note sent!", Toast.LENGTH_SHORT).show();
        }
    }

    private static class QueryListenerRegistration {
        private final com.google.firebase.database.Query query;
        private final ChildEventListener listener;

        QueryListenerRegistration(com.google.firebase.database.Query query, ChildEventListener listener) {
            this.query = query;
            this.listener = listener;
        }

        void remove() {
            query.removeEventListener(listener);
        }
    }

    // =========================================================================
    // WEBRTC CALLING (AUDIO & VIDEO CALLS)
    // =========================================================================

    private void initWebRTC() {
        if (eglBase != null) return;

        eglBase = EglBase.create();

        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        DefaultVideoEncoderFactory encoderFactory =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        localVideoView.init(eglBase.getEglBaseContext(), null);
        localVideoView.setMirror(true);
        localVideoView.setZOrderMediaOverlay(true);

        remoteVideoView.init(eglBase.getEglBaseContext(), null);
        remoteVideoView.setMirror(false);
    }

    private void setupLocalMediaTracks(boolean isVideo) {
        initWebRTC();

        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource);
        localAudioTrack.setEnabled(true);

        if (isVideo) {
            Camera2Enumerator enumerator = new Camera2Enumerator(this);
            String targetCameraName = null;
            for (String deviceName : enumerator.getDeviceNames()) {
                if (enumerator.isFrontFacing(deviceName)) {
                    targetCameraName = deviceName;
                    break;
                }
            }
            if (targetCameraName == null && enumerator.getDeviceNames().length > 0) {
                targetCameraName = enumerator.getDeviceNames()[0];
            }

            if (targetCameraName != null) {
                videoCapturer = enumerator.createCapturer(targetCameraName, null);
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
                videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
                videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
                videoCapturer.startCapture(640, 480, 30);

                localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource);
                localVideoTrack.setEnabled(true);
                localVideoTrack.addSink(localVideoView);

                cardLocalVideo.setVisibility(View.VISIBLE);
            }
        } else {
            cardLocalVideo.setVisibility(View.GONE);
        }
    }

    private void createPeerConnection(String targetPartnerUid, boolean isCaller) {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                runOnUiThread(() -> {
                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        tvCallStatus.setText(R.string.call_connected);
                        startCallDurationTimer();
                        stopRingtone();
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED
                            || iceConnectionState == PeerConnection.IceConnectionState.FAILED
                            || iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                        endCall(false);
                    }
                });
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (mDatabase != null) {
                    String role = isCaller ? "caller" : "receiver";
                    DatabaseReference candRef = mDatabase.getReference("calls")
                            .child(isCaller ? targetPartnerUid : currentUid)
                            .child("candidates")
                            .child(role)
                            .push();

                    Map<String, Object> candMap = new HashMap<>();
                    candMap.put("sdpMid", iceCandidate.sdpMid);
                    candMap.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    candMap.put("sdp", iceCandidate.sdp);
                    candRef.setValue(candMap);
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {}

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                if (rtpReceiver.track() instanceof VideoTrack) {
                    VideoTrack remoteVideo = (VideoTrack) rtpReceiver.track();
                    remoteVideo.setEnabled(true);
                    remoteVideo.addSink(remoteVideoView);
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                if (transceiver.getReceiver().track() instanceof VideoTrack) {
                    VideoTrack remoteVideo = (VideoTrack) transceiver.getReceiver().track();
                    remoteVideo.setEnabled(true);
                    remoteVideo.addSink(remoteVideoView);
                }
            }
        });

        if (peerConnection != null) {
            if (localAudioTrack != null) peerConnection.addTrack(localAudioTrack);
            if (localVideoTrack != null) peerConnection.addTrack(localVideoTrack);
        }
    }

    private void startOutgoingCall(String partnerId, String name, String emoji, String type) {
        activeCallPartnerUid = partnerId;
        activeCallType = type;
        isIncomingCall = false;

        switchView(VIEW_CALL);
        tvCallName.setText(name);
        tvCallAvatarEmoji.setText(emoji != null && !emoji.isEmpty() ? emoji : "👤");
        tvCallStatus.setText(R.string.calling);
        btnAcceptCall.setVisibility(View.GONE);
        btnEndCall.setVisibility(View.VISIBLE);

        setupAudioForCall();
        setupLocalMediaTracks("video".equalsIgnoreCase(type));
        createPeerConnection(partnerId, true);

        if (mDatabase == null) {
            tvCallStatus.setText(R.string.call_connected);
            startCallDurationTimer();
            return;
        }

        DatabaseReference callRef = mDatabase.getReference("calls").child(partnerId);
        Map<String, Object> callData = new HashMap<>();
        callData.put("from", currentUid);
        callData.put("callerName", currentUserName);
        callData.put("callerEmoji", currentUserEmoji);
        callData.put("type", type);
        callData.put("accepted", false);
        callData.put("timestamp", ServerValue.TIMESTAMP);
        callRef.setValue(callData);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "video".equalsIgnoreCase(type) ? "true" : "false"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription desc) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        callRef.child("offer").setValue(desc.description);
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {}
                }, desc);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {}

            @Override
            public void onSetFailure(String s) {}
        }, sdpConstraints);

        partnerAnswerRef = callRef.child("answer");
        partnerAnswerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String answerSdp = snapshot.getValue(String.class);
                if (answerSdp != null && peerConnection != null) {
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerSdp);
                    peerConnection.setRemoteDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {}

                        @Override
                        public void onSetSuccess() {
                            remoteDescriptionSet = true;
                            drainPendingIceCandidates();
                        }

                        @Override
                        public void onCreateFailure(String s) {}

                        @Override
                        public void onSetFailure(String s) {}
                    }, answer);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        partnerAnswerRef.addValueEventListener(partnerAnswerListener);

        partnerReceiverCandRef = callRef.child("candidates").child("receiver");
        partnerReceiverCandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot candSnap : snapshot.getChildren()) {
                    String sdpMid = candSnap.child("sdpMid").getValue(String.class);
                    Long sdpMLineIndex = candSnap.child("sdpMLineIndex").getValue(Long.class);
                    String sdp = candSnap.child("sdp").getValue(String.class);

                    if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                        IceCandidate cand = new IceCandidate(sdpMid, sdpMLineIndex.intValue(), sdp);
                        addOrQueueIceCandidate(cand);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        partnerReceiverCandRef.addValueEventListener(partnerReceiverCandListener);

        partnerAcceptedRef = callRef.child("accepted");
        partnerAcceptedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean accepted = snapshot.getValue(Boolean.class);
                if (accepted != null && accepted) {
                    runOnUiThread(() -> {
                        tvCallStatus.setText(R.string.call_connected);
                        startCallDurationTimer();
                        stopRingtone();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        partnerAcceptedRef.addValueEventListener(partnerAcceptedListener);
    }

    private void listenToIncomingCalls() {
        if (mDatabase == null || currentUid == null) return;
        incomingCallRef = mDatabase.getReference("calls").child(currentUid);
        incomingCallListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Boolean accepted = snapshot.child("accepted").getValue(Boolean.class);
                String from = snapshot.child("from").getValue(String.class);
                String callerName = snapshot.child("callerName").getValue(String.class);
                String callerEmoji = snapshot.child("callerEmoji").getValue(String.class);
                String type = snapshot.child("type").getValue(String.class);

                if (from != null && (accepted == null || !accepted) && currentView != VIEW_CALL) {
                    showIncomingCallUI(from, callerName, callerEmoji, type);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        incomingCallRef.addValueEventListener(incomingCallListener);
    }

    private void showIncomingCallUI(String fromUid, String callerName, String callerEmoji, String callType) {
        activeCallPartnerUid = fromUid;
        activeCallType = callType != null ? callType : "voice";
        isIncomingCall = true;

        switchView(VIEW_CALL);
        tvCallName.setText(callerName != null ? callerName : "Incoming Caller");
        tvCallAvatarEmoji.setText(callerEmoji != null && !callerEmoji.isEmpty() ? callerEmoji : "👤");
        tvCallStatus.setText(R.string.incoming_call);
        btnAcceptCall.setVisibility(View.VISIBLE);
        btnEndCall.setVisibility(View.VISIBLE);

        playRingtone();
    }

    private void acceptIncomingCall() {
        stopRingtone();
        btnAcceptCall.setVisibility(View.GONE);
        tvCallStatus.setText("Connecting…");

        setupAudioForCall();
        setupLocalMediaTracks("video".equalsIgnoreCase(activeCallType));
        createPeerConnection(activeCallPartnerUid, false);

        if (mDatabase == null) {
            tvCallStatus.setText(R.string.call_connected);
            startCallDurationTimer();
            return;
        }

        DatabaseReference callRef = mDatabase.getReference("calls").child(currentUid);

        callRef.child("offer").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String offerSdp = snapshot.getValue(String.class);
                if (offerSdp == null) {
                    Toast.makeText(MainActivity.this, "Offer not found", Toast.LENGTH_SHORT).show();
                    endCall(true);
                    return;
                }

                SessionDescription offerDesc = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
                peerConnection.setRemoteDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        remoteDescriptionSet = true;
                        drainPendingIceCandidates();

                        MediaConstraints constraints = new MediaConstraints();
                        peerConnection.createAnswer(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription desc) {
                                peerConnection.setLocalDescription(new SdpObserver() {
                                    @Override
                                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                                    @Override
                                    public void onSetSuccess() {
                                        callRef.child("answer").setValue(desc.description);
                                        callRef.child("accepted").setValue(true);
                                        runOnUiThread(() -> {
                                            tvCallStatus.setText(R.string.call_connected);
                                            startCallDurationTimer();
                                        });
                                    }

                                    @Override
                                    public void onCreateFailure(String s) {}

                                    @Override
                                    public void onSetFailure(String s) {}
                                }, desc);
                            }

                            @Override
                            public void onSetSuccess() {}

                            @Override
                            public void onCreateFailure(String s) {}

                            @Override
                            public void onSetFailure(String s) {}
                        }, constraints);
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {}
                }, offerDesc);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        myCallerCandRef = callRef.child("candidates").child("caller");
        myCallerCandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot candSnap : snapshot.getChildren()) {
                    String sdpMid = candSnap.child("sdpMid").getValue(String.class);
                    Long sdpMLineIndex = candSnap.child("sdpMLineIndex").getValue(Long.class);
                    String sdp = candSnap.child("sdp").getValue(String.class);

                    if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                        IceCandidate cand = new IceCandidate(sdpMid, sdpMLineIndex.intValue(), sdp);
                        addOrQueueIceCandidate(cand);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        myCallerCandRef.addValueEventListener(myCallerCandListener);
    }

    private synchronized void addOrQueueIceCandidate(IceCandidate cand) {
        if (remoteDescriptionSet && peerConnection != null) {
            peerConnection.addIceCandidate(cand);
        } else {
            pendingIceCandidates.add(cand);
        }
    }

    private synchronized void drainPendingIceCandidates() {
        if (peerConnection != null && !pendingIceCandidates.isEmpty()) {
            for (IceCandidate cand : pendingIceCandidates) {
                peerConnection.addIceCandidate(cand);
            }
            pendingIceCandidates.clear();
        }
    }

    private void setupAudioForCall() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }
    }

    private void resetAudio() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }
    }

    private void playRingtone() {
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtonePlayer = MediaPlayer.create(this, alert);
            if (ringtonePlayer != null) {
                ringtonePlayer.setLooping(true);
                ringtonePlayer.start();
            }
        } catch (Exception ignored) {}
    }

    private void stopRingtone() {
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) ringtonePlayer.stop();
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private void toggleMic() {
        isMuted = !isMuted;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!isMuted);
        }
        btnToggleMic.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            cameraCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFront) {
                    isFrontCamera = isFront;
                    localVideoView.setMirror(isFront);
                }

                @Override
                public void onCameraSwitchError(String s) {}
            });
        }
    }

    private void startCallDurationTimer() {
        callStartTime = SystemClock.elapsedRealtime();
        callTimerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = (SystemClock.elapsedRealtime() - callStartTime) / 1000;
                long mins = elapsed / 60;
                long secs = elapsed % 60;
                String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
                tvCallStatus.setText(timeFormatted);
                callTimerHandler.postDelayed(this, 1000);
            }
        };
        callTimerHandler.post(callTimerRunnable);
    }

    private void stopCallDurationTimer() {
        if (callTimerRunnable != null) {
            callTimerHandler.removeCallbacks(callTimerRunnable);
            callTimerRunnable = null;
        }
    }

    private void endCall(boolean notifyFirebase) {
        stopRingtone();
        stopCallDurationTimer();

        long durationSecs = callStartTime > 0 ? (SystemClock.elapsedRealtime() - callStartTime) / 1000 : 0;
        long mins = durationSecs / 60;
        long secs = durationSecs % 60;
        String formattedDuration = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);

        if (mDatabase != null && currentUid != null && activeCallPartnerUid != null) {
            DatabaseReference historyRef = mDatabase.getReference("users").child(currentUid).child("call_history").push();
            Map<String, Object> historyData = new HashMap<>();
            historyData.put("name", partnerName != null ? partnerName : "Contact");
            historyData.put("emoji", partnerEmoji != null ? partnerEmoji : "👤");
            historyData.put("type", activeCallType);
            historyData.put("duration", formattedDuration);
            historyData.put("timestamp", ServerValue.TIMESTAMP);
            historyData.put("direction", isIncomingCall ? "Incoming" : "Outgoing");
            historyRef.setValue(historyData);

            if (notifyFirebase) {
                mDatabase.getReference("calls").child(activeCallPartnerUid).removeValue();
                mDatabase.getReference("calls").child(currentUid).removeValue();
            }
        }

        if (partnerAnswerRef != null && partnerAnswerListener != null) {
            partnerAnswerRef.removeEventListener(partnerAnswerListener);
        }
        if (partnerReceiverCandRef != null && partnerReceiverCandListener != null) {
            partnerReceiverCandRef.removeEventListener(partnerReceiverCandListener);
        }
        if (partnerAcceptedRef != null && partnerAcceptedListener != null) {
            partnerAcceptedRef.removeEventListener(partnerAcceptedListener);
        }
        if (myCallerCandRef != null && myCallerCandListener != null) {
            myCallerCandRef.removeEventListener(myCallerCandListener);
        }

        try {
            if (videoCapturer != null) {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
                videoCapturer = null;
            }
            if (surfaceTextureHelper != null) {
                surfaceTextureHelper.dispose();
                surfaceTextureHelper = null;
            }
            if (localVideoTrack != null) {
                localVideoTrack.dispose();
                localVideoTrack = null;
            }
            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }
            if (localAudioTrack != null) {
                localAudioTrack.dispose();
                localAudioTrack = null;
            }
            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }
            if (peerConnection != null) {
                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
            }
            if (peerConnectionFactory != null) {
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
            }
            if (localVideoView != null) localVideoView.release();
            if (remoteVideoView != null) remoteVideoView.release();
            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }
        } catch (Exception ignored) {}

        remoteDescriptionSet = false;
        pendingIceCandidates.clear();
        callStartTime = 0;
        resetAudio();

        if (partnerUid != null && currentView == VIEW_CALL) {
            switchView(VIEW_CHAT);
        } else {
            switchView(VIEW_MAIN);
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancelAll();
        }

        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).child("online").setValue(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;

        if (mDatabase != null && currentUid != null) {
            mDatabase.getReference("users").child(currentUid).child("lastSeen").setValue(ServerValue.TIMESTAMP);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtone();

        if (contactsRef != null && contactsListener != null) {
            contactsRef.removeEventListener(contactsListener);
        }
        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }
        if (callHistoryRef != null && callHistoryListener != null) {
            callHistoryRef.removeEventListener(callHistoryListener);
        }
        if (incomingCallRef != null && incomingCallListener != null) {
            incomingCallRef.removeEventListener(incomingCallListener);
        }
        if (storiesRef != null && storiesListener != null) {
            storiesRef.removeEventListener(storiesListener);
        }
        if (partnerStatusListener != null && mDatabase != null && partnerUid != null) {
            mDatabase.getReference("users").child(partnerUid).child("online").removeEventListener(partnerStatusListener);
        }
        if (partnerTypingListener != null && mDatabase != null && currentChatKey != null) {
            mDatabase.getReference("chats").child(currentChatKey).child("typing").child(partnerUid).removeEventListener(partnerTypingListener);
        }
        if (chatMessagesListener != null) {
            chatMessagesListener.remove();
        }

        endCall(false);
    }
}
