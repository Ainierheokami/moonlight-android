package com.limelight.heokami;

import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.TextUtils;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;

public class EditMenuFragment extends Fragment {
    private static final int ANIMATION_DURATION = 300;
    private Game game;
    private VirtualKeyboard vk;
    private View menuPanel;

    public static EditMenuFragment newInstance(Game game, VirtualKeyboard vk){
        EditMenuFragment f = new EditMenuFragment();
        f.game = game; f.vk = vk; return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.game_menu_overlay, container, false);
        menuPanel = root.findViewById(R.id.menu_panel);
        View backgroundView = root.findViewById(R.id.menu_background);
        View mainScroll = root.findViewById(R.id.main_scroll);
        View bottomRow = root.findViewById(R.id.bottom_action_row);
        View closeButton = root.findViewById(R.id.btn_close_menu);
        // 修改标题为“虚拟键盘菜单”
        if (closeButton != null && closeButton.getParent() instanceof ViewGroup) {
            ViewGroup headerRow = (ViewGroup) closeButton.getParent();
            for (int i = 0; i < headerRow.getChildCount(); i++) {
                View child = headerRow.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setText(getString(R.string.virtual_keyboard_menu_title));
                    break;
                }
            }
        }
        // 清空默认内容，填入编辑菜单项
        if (mainScroll instanceof ScrollView){
            LinearLayout content = new LinearLayout(getActivity());
            content.setOrientation(LinearLayout.VERTICAL);
            ((ScrollView) mainScroll).removeAllViews();
            ((ScrollView) mainScroll).addView(content);
            // 标题
            TextView title = new TextView(getActivity());
            title.setText(getString(R.string.virtual_keyboard_menu_set_button_title));
            title.setTextColor(0xFFFFFFFF); title.setTextSize(18);
            int p = (int)(getResources().getDisplayMetrics().density*16);
            title.setPadding(p, p, p, p/2);
            content.addView(title);
            // 菜单项
            VirtualKeyboardMenu vkm = new VirtualKeyboardMenu(getActivity(), vk);
            vkm.setGameView(game);
            for (java.util.Map.Entry<String, Function0<Unit>> e : vkm.createActionMap().entrySet()){
                content.addView(makeMenuButton(e.getKey(), () -> { e.getValue().invoke(); }));
            }
            // 移除列表中的“撤回/重做”重复项，保留底部固定区域的操作
        }
        // 底部固定：撤销 重做 退出
        if (bottomRow instanceof LinearLayout){
            ((LinearLayout) bottomRow).removeAllViews();
            ((LinearLayout) bottomRow).setOrientation(LinearLayout.HORIZONTAL);
            ((LinearLayout) bottomRow).addView(makeBottomButton(getString(R.string.virtual_keyboard_menu_quash_history), ()-> vk.quashHistory()));
            ((LinearLayout) bottomRow).addView(makeBottomButton(getString(R.string.virtual_keyboard_menu_forward_history), ()-> vk.forwardHistory()));
            // 将“返回”改为“退出编辑”，并设置为红色强调按钮
            Button exitButton = makeBottomButton(getString(R.string.virtual_keyboard_menu_exit_edit), ()-> { vk.exitEditMode(); hideMenuWithAnimation(); });
            exitButton.setBackgroundResource(R.drawable.button_background_red_dark);
            // 取消最后一个按钮的右侧间距，给文本更多显示空间
            LinearLayout.LayoutParams exitLp = (LinearLayout.LayoutParams) exitButton.getLayoutParams();
            exitLp.setMarginEnd(0);
            exitButton.setLayoutParams(exitLp);
            ((LinearLayout) bottomRow).addView(exitButton);
        }
        // 背景/标题栏X点击退出
        backgroundView.setOnClickListener(v-> hideMenuWithAnimation());
        if (closeButton != null) closeButton.setOnClickListener(v-> hideMenuWithAnimation());
        // 设置宽度与起始位置并显示
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidthPx = (int)(400 * getResources().getDisplayMetrics().density);
        int panelWidth = Math.min(screenWidth / 3, maxWidthPx);
        ViewGroup.LayoutParams lp = menuPanel.getLayoutParams();
        lp.width = panelWidth;
        menuPanel.setLayoutParams(lp);
        menuPanel.setVisibility(View.VISIBLE);
        menuPanel.setTranslationX(panelWidth);
        showMenuWithAnimation();
        return root;
    }

    private Button makeMenuButton(String text, Runnable action){
        Button b = new Button(getActivity());
        b.setText(text); b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.button_background_dark);
        b.setTextColor(0xFFFFFFFF);
        int h = (int)(getResources().getDisplayMetrics().density*48);
        int m = (int)(getResources().getDisplayMetrics().density*8);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h); p.bottomMargin = m;
        b.setLayoutParams(p);
        int pad = (int)(getResources().getDisplayMetrics().density*16);
        b.setPadding(pad, b.getPaddingTop(), pad, b.getPaddingBottom());
        b.setOnClickListener(v-> action.run());
        return b;
    }

    private Button makeBottomButton(String text, Runnable action){
        Button b = new Button(getActivity());
        b.setText(text); b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.button_background_dark);
        b.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int)(getResources().getDisplayMetrics().density*48), 1f);
        p.setMarginEnd((int)(getResources().getDisplayMetrics().density*8));
        b.setLayoutParams(p);
        // 保持单行显示，避免在有充足空间时仍然换行
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setOnClickListener(v-> action.run());
        return b;
    }

    private void showMenuWithAnimation(){
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", menuPanel.getTranslationX(), 0);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
        EditMenu.setMenuShowing(true);
    }

    public void hideMenuWithAnimation(){
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", menuPanel.getTranslationX(), menuPanel.getWidth());
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
        animator.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator animation){
                if (getFragmentManager()!=null){
                    getFragmentManager().beginTransaction().remove(EditMenuFragment.this).commit();
                }
                EditMenu.setMenuShowing(false);
            }
        });
    }
}
