package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.opencsv.CSVReader;
import org.apache.commons.io.input.BOMInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FavoriteActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FavoriteAdapter adapter;
    private LinearLayout emptyLayout;
    private final List<FavoriteItem> favoriteItems = new ArrayList<>();

    // 更新后的正则表达式，严格匹配新格式
    private static final Pattern KEY_PATTERN = Pattern.compile("M(\\d+)_W(\\d+)_Q(\\d+)");
    private static final int COL_NUMBER = 0;
    private static final int COL_QUESTION = 4;
    private static final int COL_ANSWER = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        // 初始化工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("我的收藏");
        }

        // 初始化视图组件
        recyclerView = findViewById(R.id.recyclerView);
        emptyLayout = findViewById(R.id.tv_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavoriteAdapter();
        recyclerView.setAdapter(adapter);

        loadFavorites();
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        Set<String> favorites = prefs.getStringSet("favorites", new HashSet<>());

        if (favorites == null || favorites.isEmpty()) {
            showEmptyView();
        } else {
            new FavoriteLoader().execute(new ArrayList<>(favorites));
        }
    }

    private class FavoriteLoader extends AsyncTask<List<String>, Void, List<FavoriteItem>> {

        @Override
        protected List<FavoriteItem> doInBackground(List<String>... params) {
            List<FavoriteItem> result = new ArrayList<>();
            if (params == null || params.length == 0) return result;

            for (String key : params[0]) {
                processKey(key, result);
            }
            return result;
        }

        private void processKey(String key, List<FavoriteItem> result) {
            try {
                Matcher matcher = KEY_PATTERN.matcher(key);
                if (!matcher.matches()) {
                    Log.w("Favorite", "无效收藏键格式: " + key);
                    return;
                }

                int module = Integer.parseInt(matcher.group(1));
                int week = Integer.parseInt(matcher.group(2));
                String questionNumber = matcher.group(3);

                String filename = String.format("M%d_week%d.csv", module, week);
                if (!assetFileExists(filename)) {
                    Log.e("Favorite", "文件不存在: " + filename);
                    return;
                }

                loadQuestionFromCSV(filename, module, week, questionNumber, result);
            } catch (Exception e) {
                Log.e("Favorite", "处理收藏项失败: " + key, e);
            }
        }

        private void loadQuestionFromCSV(String filename, int module, int week,
                                         String targetNumber, List<FavoriteItem> result) {
            try (InputStream is = getAssets().open(filename);
                 BOMInputStream bomIn = new BOMInputStream(is);
                 CSVReader reader = new CSVReader(new InputStreamReader(bomIn, StandardCharsets.UTF_8))) {

                boolean isHeader = true;
                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    if (row.length > COL_ANSWER && row[COL_NUMBER].equals(targetNumber)) {
                        String question = row[COL_QUESTION].trim();
                        String answer = extractAnswer(row[COL_ANSWER].trim());

                        result.add(new FavoriteItem(
                                module, week, targetNumber, question, answer
                        ));
                        return;
                    }
                }
                Log.w("Favorite", "未找到题目: " + targetNumber);
            } catch (Exception e) {
                Log.e("Favorite", "加载CSV失败: " + filename, e);
            }
        }

        private String extractAnswer(String answerText) {
            Matcher matcher = Pattern.compile("^([A-E])", Pattern.CASE_INSENSITIVE).matcher(answerText);
            return matcher.find() ? matcher.group(1).toUpperCase() : "?";
        }

        @Override
        protected void onPostExecute(List<FavoriteItem> items) {
            if (items == null || items.isEmpty()) {
                showEmptyView();
            } else {
                favoriteItems.clear();
                favoriteItems.addAll(items);
                adapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.VISIBLE);
                emptyLayout.setVisibility(View.GONE);
            }
        }
    }

    private class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_question, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FavoriteItem item = favoriteItems.get(position);
            holder.tvModule.setText("模块 " + item.module);
            holder.tvWeek.setText("第 " + item.week + " 周");
            holder.tvQuestion.setText(item.question);
            holder.tvAnswer.setText("答案：" + item.answer);

            // 修改点击监听器的参数传递逻辑
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FavoriteActivity.this, MainActivity.class);
                intent.putExtra("target_module", item.module);
                intent.putExtra("target_week", item.week);
                intent.putExtra("target_question", item.questionNumber); // 确保传递题号
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return favoriteItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvModule, tvWeek, tvQuestion, tvAnswer;

            ViewHolder(View itemView) {
                super(itemView);
                tvModule = itemView.findViewById(R.id.tv_module);
                tvWeek = itemView.findViewById(R.id.tv_week);
                tvQuestion = itemView.findViewById(R.id.tv_question);
                tvAnswer = itemView.findViewById(R.id.tv_answer);
            }
        }
    }

    private void showEmptyView() {
        recyclerView.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.VISIBLE);
    }

    private boolean assetFileExists(String filename) {
        try (InputStream is = getAssets().open(filename)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static class FavoriteItem {
        final int module;
        final int week;
        final String questionNumber;
        final String question;
        final String answer;

        FavoriteItem(int module, int week, String questionNumber,
                     String question, String answer) {
            this.module = module;
            this.week = week;
            this.questionNumber = questionNumber;
            this.question = question;
            this.answer = answer;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}