package com.example.soundattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button teacher = findViewById(R.id.btnTeacher);
        Button student = findViewById(R.id.btnStudent);

        teacher.setOnClickListener(v -> startActivity(new Intent(this, TeacherActivity.class)));
        student.setOnClickListener(v -> startActivity(new Intent(this, StudentActivity.class)));
    }
}
