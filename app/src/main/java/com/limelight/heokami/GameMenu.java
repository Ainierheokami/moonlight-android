package com.limelight.heokami;

import com.limelight.Game;
import com.limelight.nvstream.NvConnection;

/**
 * 游戏菜单管理器
 * 使用新的侧边菜单Fragment替代原来的AlertDialog
 */
public class GameMenu {

    private final Game game;
    private final NvConnection conn;
    
    // 静态变量跟踪菜单是否已显示
    private static boolean isMenuShowing = false;

    /**
     * 构造函数
     * @param game 游戏Activity实例
     * @param conn 网络连接实例
     */
    public GameMenu(Game game, NvConnection conn) {
        this.game = game;
        this.conn = conn;

        showMenu();
    }

    /**
     * 显示游戏菜单
     * 使用新的侧边菜单Fragment替代原来的AlertDialog
     */
    private void showMenu() {
        android.util.Log.d("GameMenu", "showMenu called, isMenuShowing: " + isMenuShowing);
        
        // 如果菜单已经显示，则不重复显示
        if (isMenuShowing) {
            android.util.Log.d("GameMenu", "Menu already showing, returning");
            return;
        }
        
        isMenuShowing = true;
        android.util.Log.d("GameMenu", "Setting isMenuShowing to true");
        
        // 创建并显示新的侧边菜单Fragment
        GameMenuFragment fragment = GameMenuFragment.newInstance(game, conn);
        game.getFragmentManager()
            .beginTransaction()
            .add(android.R.id.content, fragment, "GameMenu")
            .commit();
        
        android.util.Log.d("GameMenu", "Menu fragment added to transaction");
    }
    
    /**
     * 检查菜单是否正在显示
     * @return 如果菜单正在显示返回true
     */
    public static boolean isMenuShowing() {
        return isMenuShowing;
    }
    
    /**
     * 设置菜单显示状态
     * @param showing 菜单是否显示
     */
    public static void setMenuShowing(boolean showing) {
        isMenuShowing = showing;
    }
}
