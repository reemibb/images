package com.example.internlink;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class StudentAnnounce extends AppCompatActivity implements AnnouncementAdapter.AnnouncementClickListener {

    private LinearLayout announcementContainer;
    private RecyclerView recyclerView;
    private TextInputEditText searchEditText;
    private ChipGroup chipGroup;
    private AnnouncementAdapter adapter;
    private MaterialToolbar toolbar;
    private List<Announcement> announcementList = new ArrayList<>();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_announce);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.announcement_recycler_view);
        searchEditText = findViewById(R.id.search_edit_text);
        chipGroup = findViewById(R.id.filter_chip_group);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AnnouncementAdapter(announcementList, new AnnouncementAdapter.AnnouncementClickListener() {
            @Override
            public void showAnnouncementPopup(String id, String title, String body, String date) {
                StudentAnnounce.this.showAnnouncementPopup(id, title, body, date); // ☑ this calls the actual method in your Activity
            }
        });

        recyclerView.setAdapter(adapter);

        toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        loadAllAnnouncements();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filterBy(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Replace the existing chipGroup.setOnCheckedChangeListener with this:
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                String chipText = chip.getText().toString();
                switch (chipText.toLowerCase()) {
                    case "earliest":
                        adapter.sortByDate(true);  // true for ascending (earliest first)
                        break;
                    case "latest":
                        adapter.sortByDate(false); // false for descending (latest first)
                        break;
                    default:
                        adapter.filterChip(chipText); // for All/Read/Unread filters
                        break;
                }
            }
        });
    }

    private void addAnnouncement(String announcementId, String title, String body, String date, boolean isRead, long timestamp) {
        Announcement announcement = new Announcement(announcementId, title, body, date, isRead);
        announcement.setTimestamp(timestamp);
        announcementList.add(announcement);
        adapter.notifyItemInserted(announcementList.size() - 1);
    }

    // UPDATED: Enhanced popup method to handle clickable links for students
    @Override
    public void showAnnouncementPopup(String announcementId, String title, String body, String date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View popupView = LayoutInflater.from(this).inflate(R.layout.announcement_item, null);

        // Find views
        TextView titleView = popupView.findViewById(R.id.announcement_title);
        TextView bodyView = popupView.findViewById(R.id.announcement_body);
        TextView dateView = popupView.findViewById(R.id.announcement_date);
        ImageView closeIcon = popupView.findViewById(R.id.delete_icon);

        // Find the announcement object
        Announcement announcement = null;
        for (Announcement a : announcementList) {
            if (a.getId().equals(announcementId)) {
                announcement = a;
                break;
            }
        }

        // Apply warning-specific styling if needed
        if (announcement != null && "warning".equals(announcement.getCategory())) {
            titleView.setTextColor(Color.RED);
            //popupView.findViewById(R.id.warning_icon).setVisibility(View.VISIBLE);

            // Add severity indicator
            /*if ("high".equals(announcement.getPriority())) {
                popupView.findViewById(R.id.priority_indicator).setVisibility(View.VISIBLE);
            }*/
        }

        titleView.setText(title);
        bodyView.setText(body);
        dateView.setText("Posted: " + date);

        builder.setView(popupView);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        closeIcon.setOnClickListener(v -> dialog.dismiss());

        // Mark the announcement as read
        markAnnouncementAsRead(announcementId);
    }

    // NEW: Method to create clickable spans for student-specific links
    private SpannableString createClickableSpannable(String body) {
        SpannableString spannable = new SpannableString(body);

        // Handle [View Details] links - navigate to applications
        handleClickableLink(spannable, body, "[View Details]", () -> {
            Intent intent = new Intent(StudentAnnounce.this, StudentHomeActivity.class);
            startActivity(intent);
        });

        // Handle [View Status] links - navigate to applications
        handleClickableLink(spannable, body, "[View Status]", () -> {
            Intent intent = new Intent(StudentAnnounce.this, StudentHomeActivity.class);
            startActivity(intent);
        });

        return spannable;
    }

    // NEW: Helper method to handle clickable links
    private void handleClickableLink(SpannableString spannable, String body, String linkText, Runnable action) {
        int start = body.indexOf(linkText);
        if (start != -1) {
            int end = start + linkText.length();

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    action.run();
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true);
                    ds.setColor(Color.BLUE);
                }
            };

            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    // NEW: Method to mark announcement as read (similar to company version)
    private void markAnnouncementAsRead(String announcementId) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userReadsRef = FirebaseDatabase.getInstance()
                .getReference("user_reads")
                .child(userId)
                .child(announcementId);

        userReadsRef.setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    for (Announcement announcement : announcementList) {
                        if (announcement.getId().equals(announcementId)) {
                            announcement.setRead(true);
                            adapter.notifyDataSetChanged();
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(StudentAnnounce.this, "Failed to mark as read", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadAllAnnouncements() {
        announcementList.clear();
        adapter.notifyDataSetChanged();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userReadsRef = FirebaseDatabase.getInstance().getReference("user_reads").child(userId);

        userReadsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot readsSnapshot) {
                // Load general announcements
                loadAnnouncementsFromRef(FirebaseDatabase.getInstance().getReference("announcements"), readsSnapshot);

                // Load student-specific announcements - modified to check recipientId and removed type setting
                DatabaseReference studentAnnouncementsRef = FirebaseDatabase.getInstance()
                        .getReference("announcements_by_role")
                        .child("student");

                studentAnnouncementsRef.orderByChild("recipientId")
                        .equalTo(userId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {
                                for (DataSnapshot snap : snapshot.getChildren()) {
                                    try {
                                        String id = snap.getKey();
                                        String title = snap.child("title").getValue(String.class);
                                        String body = snap.child("message").getValue(String.class);

                                        // Handle timestamp conversion safely
                                        long timestampLong = 0;
                                        Object timestampObj = snap.child("timestamp").getValue();
                                        if (timestampObj != null) {
                                            if (timestampObj instanceof Long) {
                                                timestampLong = (Long) timestampObj;
                                            } else if (timestampObj instanceof String) {
                                                try {
                                                    timestampLong = Long.parseLong((String) timestampObj);
                                                } catch (NumberFormatException e) {
                                                    try {
                                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                                                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                                        Date date = sdf.parse((String) timestampObj);
                                                        if (date != null) {
                                                            timestampLong = date.getTime();
                                                        }
                                                    } catch (ParseException pe) {
                                                        pe.printStackTrace();
                                                    }
                                                }
                                            }
                                        }

                                        String date = formatTimestamp(timestampLong);
                                        boolean isRead = readsSnapshot.hasChild(id);

                                        // Create announcement without setting type
                                        Announcement announcement = new Announcement(id, title, body, date, isRead);
                                        announcement.setTimestamp(timestampLong);

                                        // Only set category for application status announcements
                                        if (snap.hasChild("applicant_status")) {
                                            announcement.setCategory("application_status");
                                        }

                                        announcementList.add(announcement);
                                        adapter.notifyItemInserted(announcementList.size() - 1);
                                    } catch (Exception e) {
                                        Log.e("StudentAnnounce", "Error processing student announcement: " + snap.getKey(), e);
                                    }
                                }
                                // Sort announcements after adding all
                                sortAnnouncementsByDate(false);
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                Toast.makeText(StudentAnnounce.this, "Failed to load student announcements", Toast.LENGTH_SHORT).show();
                            }
                        });

                // Load warning announcements specifically for this user
                loadWarningAnnouncements(readsSnapshot, userId);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(StudentAnnounce.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void loadWarningAnnouncements(DataSnapshot readsSnapshot, String userId) {
        DatabaseReference warningsRef = FirebaseDatabase.getInstance()
                .getReference("announcements_by_role")
                .child("student");

        warningsRef.orderByChild("targetUserId").equalTo(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String id = snap.getKey();
                    String category = snap.child("category").getValue(String.class);

                    // Only process if it's a warning announcement
                    if ("disciplinary_action".equals(category)) {
                        String title = snap.child("title").getValue(String.class);
                        String message = snap.child("message").getValue(String.class);

                        // Handle timestamp conversion safely
                        long timestampLong = 0;
                        Object timestampObj = snap.child("timestamp").getValue();
                        if (timestampObj != null) {
                            if (timestampObj instanceof Long) {
                                timestampLong = (Long) timestampObj;
                            } else if (timestampObj instanceof String) {
                                try {
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    Date date = sdf.parse((String) timestampObj);
                                    if (date != null) {
                                        timestampLong = date.getTime();
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        String severity = snap.child("severity").getValue(String.class);
                        String priority = snap.child("priority").getValue(String.class);
                        boolean isRead = readsSnapshot.hasChild(id);

                        // Create announcement
                        Announcement announcement = new Announcement(id, title, message, formatTimestamp(timestampLong), isRead);
                        announcement.setTimestamp(timestampLong);
                        announcement.setCategory("warning");
                        announcement.setSeverity(severity);
                        announcement.setPriority(priority);

                        announcementList.add(announcement);
                        adapter.notifyItemInserted(announcementList.size() - 1);
                    }
                }
                // Sort announcements after adding warnings
                sortAnnouncementsByDate(false);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(StudentAnnounce.this, "Failed to load warnings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAnnouncementsFromRef(DatabaseReference ref, DataSnapshot readsSnapshot) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot snap : snapshot.getChildren()) {
                    try {
                        String id = snap.getKey();
                        String title = snap.child("title").getValue(String.class);
                        String body = snap.child("message").getValue(String.class);

                        // Handle timestamp conversion safely
                        long timestampLong = 0;
                        DataSnapshot timestampSnap = snap.child("timestamp");
                        if (timestampSnap.exists()) {
                            Object timestampValue = timestampSnap.getValue();
                            if (timestampValue instanceof Long) {
                                timestampLong = (Long) timestampValue;
                            } else if (timestampValue instanceof String) {
                                String timestampStr = (String) timestampValue;
                                try {
                                    if (timestampStr.contains("T")) {
                                        // ISO 8601 format
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                        Date date = sdf.parse(timestampStr);
                                        if (date != null) {
                                            timestampLong = date.getTime();
                                        }
                                    } else {
                                        // Numeric string format
                                        timestampLong = Long.parseLong(timestampStr);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else if (timestampValue instanceof Double) {
                                // Handle double timestamp
                                timestampLong = ((Double) timestampValue).longValue();
                            }
                        }

                        String date = formatTimestamp(timestampLong);
                        boolean isRead = readsSnapshot.hasChild(id);

                        // Create announcement with basic info
                        Announcement announcement = new Announcement(id, title, body, date, isRead);
                        announcement.setTimestamp(timestampLong);

                        // Check if this is a warning announcement
                        if (snap.hasChild("category") && "disciplinary_action".equals(snap.child("category").getValue(String.class))) {
                            announcement.setCategory("warning");
                            announcement.setSeverity(snap.child("severity").getValue(String.class));
                            announcement.setPriority(snap.child("priority").getValue(String.class));
                        }

                        announcementList.add(announcement);
                    } catch (Exception e) {
                        Log.e("StudentAnnounce", "Error processing announcement: " + snap.getKey(), e);
                    }
                }

                // Sort and update UI after adding all announcements
                sortAnnouncementsByDate(false);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(StudentAnnounce.this, "Failed to load announcements", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "Unknown date";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            Log.e("StudentAnnounce", "Error formatting timestamp: " + timestamp, e);
            return "Unknown date";
        }
    }

    private void sortAnnouncementsByDate(boolean ascending) {
        if (announcementList == null || announcementList.isEmpty()) return;

        Collections.sort(announcementList, (a1, a2) -> {
            // Handle null or invalid timestamps
            long t1 = a1 != null ? a1.getTimestamp() : 0;
            long t2 = a2 != null ? a2.getTimestamp() : 0;

            return ascending ? Long.compare(t1, t2) : Long.compare(t2, t1);
        });

        adapter.notifyDataSetChanged();
    }
}
