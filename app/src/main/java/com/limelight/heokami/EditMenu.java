package com.limelight.heokami;

import com.limelight.Game;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;

public class EditMenu {
    private final Game game;
    private final VirtualKeyboard vk;

    private static boolean isMenuShowing = false;

    public EditMenu(Game game, VirtualKeyboard vk) {
        this.game = game;
        this.vk = vk;
        showMenu();
    }

    private void showMenu(){
        if (isMenuShowing) return;
        isMenuShowing = true;
        EditMenuFragment fragment = EditMenuFragment.newInstance(game, vk);
        game.getFragmentManager()
            .beginTransaction()
            // 将编辑菜单添加到专用的菜单容器，确保高于虚拟输入层
            .add(com.limelight.R.id.menuOverlayContainer, fragment, "EditMenu")
            .commit();
    }

    public static boolean isMenuShowing(){
        return isMenuShowing;
    }

    public static void setMenuShowing(boolean showing){
        isMenuShowing = showing;
    }
}
