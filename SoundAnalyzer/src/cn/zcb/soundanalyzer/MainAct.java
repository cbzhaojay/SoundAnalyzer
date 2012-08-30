package cn.zcb.soundanalyzer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import cn.zcb.soundanalyzer.audiocore.AudioAnalyzer;
import cn.zcb.soundanalyzer.audiocore.PaintBoard;

public class MainAct extends Activity {
	AudioAnalyzer analyzer;
	PaintBoard paintBoard;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        paintBoard=(PaintBoard) findViewById(R.id.paintBoard);
    }
    public void initAnalyzer(){
    	analyzer=new AudioAnalyzer(paintBoard);
    	analyzer.measureStart();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
