package com.app.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.code_intelligence.jazzer.runtime.CoverageMap;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the layout programmatically
        TextView textView = new TextView(this);
        textView.setText("Hello, AOSP!!");
        textView.setId(View.generateViewId());

        Button button = new Button(this);
        button.setText("Click Me");
        button.setId(View.generateViewId());

        // Layout container
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Add TextView and Button to the layout
        layout.addView(textView);
        layout.addView(button);

        // Set the layout as the content view
        setContentView(layout);

        // Set button click listener to change the text
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("Button Clicked!");
            }
        });
    }
}
