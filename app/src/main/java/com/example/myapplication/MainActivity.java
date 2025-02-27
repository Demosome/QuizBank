package com.example.myapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.opencsv.CSVReader;
import org.apache.commons.io.input.BOMInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "QuestionFavorites";
    private static final int MAX_MODULES = 8;
    private static final int MAX_WEEKS = 8;

    // UI组件
    private Spinner moduleSpinner, weekSpinner;
    private LinearLayout selectorLayout, questionLayout;
    private TextView tvQuestionNumber, tvQuestion, tvAnswer;
    private Button btnA, btnB, btnC, btnD, btnE;
    private Button btnPrevious, btnNext, btnBack, btnJump, btnFavorite;

    // 数据相关
    private List<Question> questionList = new ArrayList<>();
    private int currentIndex = 0;
    private int currentModule = 1, currentWeek = 1;
    private QuestionLoader currentLoader;
    private SharedPreferences favoritesPref;
    private String pendingJumpQuestion;

    // 题目数据结构
    static class Question {
        final String number;
        final String question;
        final String options;
        final String correctAnswer;

        Question(String number, String question, String options, String correctAnswer) {
            this.number = number;
            this.question = question;
            this.options = options;
            this.correctAnswer = correctAnswer;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        favoritesPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initializeComponents();
        setupUI();
        setupListeners();
        handleIntentParams(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentParams(intent);
    }

    private void initializeComponents() {
        moduleSpinner = findViewById(R.id.module_spinner);
        weekSpinner = findViewById(R.id.week_spinner);
        selectorLayout = findViewById(R.id.selector_layout);
        questionLayout = findViewById(R.id.question_layout);
        tvQuestionNumber = findViewById(R.id.tv_question_number);
        tvQuestion = findViewById(R.id.tv_question);
        tvAnswer = findViewById(R.id.tv_answer);
        btnA = findViewById(R.id.btn_a);
        btnB = findViewById(R.id.btn_b);
        btnC = findViewById(R.id.btn_c);
        btnD = findViewById(R.id.btn_d);
        btnE = findViewById(R.id.btn_e);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
        btnBack = findViewById(R.id.btn_back);
        btnJump = findViewById(R.id.btn_jump);
        btnFavorite = findViewById(R.id.btn_favorite);
    }

    private void setupUI() {
        ArrayAdapter<String> moduleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                generateSelectionItems(MAX_MODULES, "模块", "")
        );
        moduleSpinner.setAdapter(moduleAdapter);

        ArrayAdapter<String> weekAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                generateSelectionItems(MAX_WEEKS, "第", "周")
        );
        weekSpinner.setAdapter(weekAdapter);
    }

    private List<String> generateSelectionItems(int count, String prefix, String suffix) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(prefix + i + suffix);
        }
        return items;
    }

    private void handleIntentParams(Intent intent) {
        if (intent != null && intent.hasExtra("target_module")) {
            currentModule = intent.getIntExtra("target_module", 1);
            currentWeek = intent.getIntExtra("target_week", 1);
            pendingJumpQuestion = intent.getStringExtra("target_question");

            moduleSpinner.setSelection(currentModule - 1);
            weekSpinner.setSelection(currentWeek - 1);
            loadQuestions();
        }
    }

    private void loadQuestions() {
        currentModule = moduleSpinner.getSelectedItemPosition() + 1;
        currentWeek = weekSpinner.getSelectedItemPosition() + 1;
        String fileName = String.format("M%d_week%d.csv", currentModule, currentWeek);

        if (!checkAssetExists(fileName)) {
            showAlert("文件未找到", "题库文件不存在: " + fileName);
            return;
        }

        if (currentLoader != null) currentLoader.cancel(true);
        currentLoader = new QuestionLoader(fileName);
        currentLoader.execute();
    }

    private boolean checkAssetExists(String fileName) {
        try {
            return Arrays.asList(getAssets().list("")).contains(fileName);
        } catch (IOException e) {
            return false;
        }
    }

    private void jumpToQuestion(String targetQuestion) {
        if (targetQuestion == null || questionList.isEmpty()) return;

        for (int i = 0; i < questionList.size(); i++) {
            if (questionList.get(i).number.equals(targetQuestion)) {
                currentIndex = i;
                displayQuestion();
                return;
            }
        }
        showToast("未找到题目: " + targetQuestion);
    }

    private void displayQuestion() {
        Question q = getCurrentQuestion();
        if (q == null) return;

        tvQuestionNumber.setText(String.format("题目 %d/%d", currentIndex + 1, questionList.size()));
        tvQuestion.setText(q.question);
        tvAnswer.setVisibility(View.GONE);

        // 选项分割逻辑
        String optionContent = q.options.trim();
        List<String> options = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?i)(?<=\\s|^)([A-E])\\.");
        Matcher matcher = pattern.matcher(optionContent);

        int lastStart = 0;
        boolean isFirst = true;

        while (matcher.find()) {
            if (isFirst) {
                isFirst = false;
                continue;
            }

            int end = matcher.start();
            String option = optionContent.substring(lastStart, end).trim();
            options.add(option);
            lastStart = end;
        }
        if (lastStart < optionContent.length()) {
            options.add(optionContent.substring(lastStart).trim());
        }

        Button[] optionButtons = {btnA, btnB, btnC, btnD, btnE};
        for (int i = 0; i < optionButtons.length; i++) {
            if (i < options.size()) {
                String formatted = options.get(i)
                        .replaceFirst("(?i)^([a-e])\\.", "$1.");
                optionButtons[i].setText(formatted);
                optionButtons[i].setVisibility(View.VISIBLE);
            } else {
                optionButtons[i].setVisibility(View.GONE);
            }
        }

        resetOptionAppearance();
        updateFavoriteButton();
    }

    private class QuestionLoader extends AsyncTask<Void, Void, List<Question>> {
        private final String fileName;
        private ProgressDialog loadingDialog;

        private static final int COL_INDEX_NUMBER = 0;
        private static final int COL_INDEX_QUESTION = 4;
        private static final int COL_INDEX_OPTIONS = 5;
        private static final int COL_INDEX_ANSWER = 6;

        QuestionLoader(String fileName) {
            this.fileName = fileName;
        }

        @Override
        protected void onPreExecute() {
            loadingDialog = new ProgressDialog(MainActivity.this);
            loadingDialog.setMessage("加载题目中...");
            loadingDialog.setCancelable(false);
            loadingDialog.show();
        }

        @Override
        protected List<Question> doInBackground(Void... voids) {
            List<Question> loadedQuestions = new ArrayList<>();
            try (InputStream is = getAssets().open(fileName);
                 BOMInputStream bomIn = new BOMInputStream(is);
                 CSVReader reader = new CSVReader(new InputStreamReader(bomIn, StandardCharsets.UTF_8))) {

                boolean isFirstRow = true;
                String[] csvRow;
                while ((csvRow = reader.readNext()) != null) {
                    if (isFirstRow) {
                        isFirstRow = false;
                        continue;
                    }
                    if (validateCsvRow(csvRow)) {
                        loadedQuestions.add(new Question(
                                csvRow[COL_INDEX_NUMBER].trim(),
                                csvRow[COL_INDEX_QUESTION].trim(),
                                csvRow[COL_INDEX_OPTIONS].trim(),
                                extractAnswerInitial(csvRow[COL_INDEX_ANSWER].trim())
                        ));
                    }
                }
            } catch (Exception e) {
                Log.e("CSV_Error", "加载失败: " + fileName, e);
                return null;
            }
            return loadedQuestions;
        }

        private boolean validateCsvRow(String[] row) {
            return row.length > COL_INDEX_ANSWER &&
                    !row[COL_INDEX_QUESTION].trim().isEmpty() &&
                    extractAnswerInitial(row[COL_INDEX_ANSWER].trim()).matches("[A-E]");
        }

        @Override
        protected void onPostExecute(List<Question> result) {
            loadingDialog.dismiss();
            if (result == null || result.isEmpty()) {
                showAlert("加载错误", "文件格式错误或内容为空");
                return;
            }
            questionList = result;
            selectorLayout.setVisibility(View.GONE);
            questionLayout.setVisibility(View.VISIBLE);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (pendingJumpQuestion != null) {
                    jumpToQuestion(pendingJumpQuestion);
                    pendingJumpQuestion = null;
                } else {
                    displayQuestion();
                }
            }, 200);
        }
    }

    // 其他辅助方法
    private void handleOptionSelection(Button selectedButton) {
        String userAnswer = extractAnswerInitial(selectedButton.getText().toString());
        String correctAnswer = getCurrentQuestion().correctAnswer;

        highlightCorrectAnswer(correctAnswer);

        if (userAnswer.equalsIgnoreCase(correctAnswer)) {
            selectedButton.setBackgroundColor(ContextCompat.getColor(this, R.color.correct_green));
        } else {
            selectedButton.setBackgroundColor(Color.RED);
        }

        tvAnswer.setVisibility(View.VISIBLE);
        tvAnswer.setText("正确答案：" + correctAnswer);
        btnFavorite.setVisibility(View.VISIBLE);
    }

    private String extractAnswerInitial(String answerText) {
        Matcher matcher = Pattern.compile("^([A-Ea-e])").matcher(answerText);
        return matcher.find() ? matcher.group(1).toUpperCase() : "";
    }

    private void highlightCorrectAnswer(String answer) {
        String cleanAnswer = extractAnswerInitial(answer);
        tvAnswer.setText("正确答案：" + cleanAnswer);
        tvAnswer.setVisibility(View.VISIBLE);

        Button targetBtn = null;
        switch (cleanAnswer) {
            case "A": targetBtn = btnA; break;
            case "B": targetBtn = btnB; break;
            case "C": targetBtn = btnC; break;
            case "D": targetBtn = btnD; break;
            case "E": targetBtn = btnE; break;
        }
        if (targetBtn != null) {
            targetBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.correct_green));
        }
    }

    private void resetOptionAppearance() {
        int defaultColor = ContextCompat.getColor(this, R.color.white);
        btnA.setBackgroundColor(defaultColor);
        btnB.setBackgroundColor(defaultColor);
        btnC.setBackgroundColor(defaultColor);
        btnD.setBackgroundColor(defaultColor);
        btnE.setBackgroundColor(defaultColor);
    }

    private void toggleFavoriteStatus() {
        Question current = getCurrentQuestion();
        if (current == null) return;

        String uniqueKey = String.format("M%d_W%d_Q%s", currentModule, currentWeek, current.number);
        Set<String> original = favoritesPref.getStringSet("favorites", new HashSet<>());
        Set<String> favorites = new HashSet<>(original);

        if (favorites.contains(uniqueKey)) {
            favorites.remove(uniqueKey);
            showToast("已取消收藏");
        } else {
            favorites.add(uniqueKey);
            showToast("已收藏本题");
        }

        favoritesPref.edit().putStringSet("favorites", favorites).apply();
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        String uniqueKey = String.format("M%d_W%d_Q%s", currentModule, currentWeek, getCurrentQuestion().number);
        boolean isFavorite = favoritesPref.getStringSet("favorites", new HashSet<>()).contains(uniqueKey);
        btnFavorite.setText(isFavorite ? "★ 已收藏" : "☆ 收藏本题");
    }

    private Question getCurrentQuestion() {
        return (questionList.isEmpty() || currentIndex >= questionList.size()) ? null : questionList.get(currentIndex);
    }

    private void navigateQuestions(int direction) {
        int newIndex = currentIndex + direction;
        if (newIndex >= 0 && newIndex < questionList.size()) {
            currentIndex = newIndex;
            displayQuestion();
        }
    }

    private void showJumpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("题目跳转").setMessage("输入题号 (1-" + questionList.size() + ")");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("跳转", (dialog, which) -> {
            try {
                int target = Integer.parseInt(input.getText().toString()) - 1;
                if (target >= 0 && target < questionList.size()) {
                    currentIndex = target;
                    displayQuestion();
                }
            } catch (NumberFormatException e) {
                showToast("请输入有效数字");
            }
        });
        builder.show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showSelectionScreen() {
        questionLayout.setVisibility(View.GONE);
        selectorLayout.setVisibility(View.VISIBLE);
        resetOptionAppearance();
        questionList.clear();
        currentIndex = 0;
    }

    private void setupListeners() {
        findViewById(R.id.btn_confirm).setOnClickListener(v -> loadQuestions());

        View.OnClickListener optionListener = v -> {
            handleOptionSelection((Button) v);
            btnFavorite.setVisibility(View.VISIBLE);
        };
        btnA.setOnClickListener(optionListener);
        btnB.setOnClickListener(optionListener);
        btnC.setOnClickListener(optionListener);
        btnD.setOnClickListener(optionListener);
        btnE.setOnClickListener(optionListener);

        btnPrevious.setOnClickListener(v -> navigateQuestions(-1));
        btnNext.setOnClickListener(v -> navigateQuestions(1));
        btnBack.setOnClickListener(v -> showSelectionScreen());
        btnJump.setOnClickListener(v -> showJumpDialog());
        btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());
        findViewById(R.id.btn_show_favorites).setOnClickListener(v ->
                startActivity(new Intent(this, FavoriteActivity.class)));
    }
}