/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import com.limelight.heokami.GameGridLines;
import com.limelight.heokami.VirtualKeyboardMenu;

public abstract class VirtualKeyboardElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;
    public int elementId;
    public int layer;
    public String vk_code;
    public String text;
    public int icon;
    public float radius;
    public int opacity;
    public ButtonType buttonType = ButtonType.Button;
    public JSONObject buttonData;
    public Boolean isHide = false;
    public int group = -1;



    protected VirtualKeyboard virtualKeyboard;


    private final Paint paint = new Paint();

    public int normalColor = 0xF0888888;
    public int pressedColor = 0xF00000FF;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSettingsColor = 0xF090e494;
    private int configSelectedColor = 0xF000FF00;

    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private enum Mode {
        Normal,
        Resize,
        Move,
        Settings
    }

    public enum ButtonType {
        Button,
        HotKeys
    }

    private Mode currentMode = Mode.Normal;

    // 网格吸附
    protected GameGridLines gridLines;
    public void setGridLines(GameGridLines gridLines) {
        this.gridLines = gridLines;
    }

    private int snapToGrid(int value, int gridSize) {
        if (gridSize == 0) return value; // 避免除以 0
        int gridValue = value / gridSize; // 计算 value 位于哪个网格单元格
        int offset = value - gridValue * gridSize; // 计算 value 与最近的网格线的偏移量
        if (Math.abs(offset) < gridLines.getSnapThreshold() && gridLines.getVisibility() == View.VISIBLE ) { // 判断偏移量是否小于阈值
            return gridValue * gridSize; // 吸附到最近的网格线
        } else {
            return value; // 不吸附
        }
    }

    protected VirtualKeyboardElement(VirtualKeyboard virtualKeyboard, Context context, int elementId, int layer){
        super(context);
        this.virtualKeyboard = virtualKeyboard;
        this.elementId = elementId;
        this.layer = layer;
        this.text = "";
        this.icon = -1;
        this.vk_code = "";
        this.radius = 10f;
        this.opacity = 85;
        try {
            this.buttonData = new JSONObject("{}");
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
        }
    }

    protected void moveGroupElement(int pressed_x, int pressed_y, int x, int y) {
        if (currentMode != Mode.Move) {
            return;
        }
        for (VirtualKeyboardElement element : virtualKeyboard.getElements()) {
            if (group != -1 && element.group == group && element.elementId != elementId) {
                element.moveElement(pressed_x, pressed_y, x, y);
            }
        }
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        // 吸附逻辑
        newPos_x = snapToGrid(newPos_x, gridLines.getCellWidth());
        newPos_y = snapToGrid(newPos_y, gridLines.getCellHeight());

        Log.d("vk", "吸附："+newPos_x+","+newPos_y+"cell:"+gridLines.getCellWidth()+","+gridLines.getCellHeight());

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        //边界检测
        FrameLayout parent = (FrameLayout)getParent();

        int maxX = parent.getWidth() - getWidth();
        int maxY = parent.getHeight() - getHeight();

        layoutParams.leftMargin = Math.max(0, Math.min(newPos_x, maxX));
        layoutParams.topMargin = Math.max(0, Math.min(newPos_y, maxY));
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
        moveGroupElement(pressed_x, pressed_y, x , y);
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        // 吸附逻辑 - 宽度和高度都需要吸附
        newWidth = snapToGrid(newWidth, gridLines.getCellWidth());
        newHeight = snapToGrid(newHeight, gridLines.getCellHeight());

        Log.d("vk", "resize吸附："+newWidth+","+newHeight+"cell:"+gridLines.getCellWidth()+","+gridLines.getCellHeight());

        layoutParams.height = Math.max(20, newHeight); // 保证最小尺寸
        layoutParams.width = Math.max(20, newWidth);   // 保证最小尺寸

        requestLayout();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        onElementDraw(canvas);

        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionEnableSettings() {
        currentMode = Mode.Settings;
        Context context = getContext();
        VirtualKeyboardMenu virtualKeyboardMenu = new VirtualKeyboardMenu(context, virtualKeyboard);
        virtualKeyboardMenu.setElement(this);
        virtualKeyboardMenu.setButtonDialog();
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.ResizeButtons)
            return configResizeColor;
        else if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.SettingsButtons)
            return configSettingsColor;
        else
            return normalColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    public int getLeftMargin() {
        return ((FrameLayout.LayoutParams) getLayoutParams()).leftMargin;
    }

    public int getTopMargin() {
        return ((FrameLayout.LayoutParams) getLayoutParams()).topMargin;
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle("Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Move",
                "Resize",
            /*election
            "Set n
            Disable color sormal color",
            "Set pressed color",
            */
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // move
                        actionEnableMove();
                        break;
                    }
                    case 1: { // resize
                        actionEnableResize();
                        break;
                    }
                /*
                case 2: { // set default color
                    actionShowNormalColorChooser();
                    break;
                }
                case 3: { // set pressed color
                    actionShowPressedColorChooser();
                    break;
                }
                */
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        // show menu
        alert.show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.MoveButtons){
                    actionEnableMove();
                }
                else if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.ResizeButtons) {
                    actionEnableResize();
                }
                else if (virtualKeyboard.getControllerMode() == VirtualKeyboard.ControllerMode.SettingsButtons){
                    actionEnableSettings();
                }

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Settings: {
//                        Context context = getContext();
//                        VirtualKeyboardMenu virtualKeyboardMenu = new VirtualKeyboardMenu(context);
//                        virtualKeyboardMenu.showMenu();
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                virtualKeyboard.addHistory();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    public void setLayer(int layer) {
        this.layer = layer;
        invalidate();
    }

    public void setVkCode(String vk_code) {
        this.vk_code = vk_code;
        invalidate();
    }

    public void setRadius(float radius) {
        this.radius = radius;
        invalidate();
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;
        invalidate();
    }

    public void setType(ButtonType type) {
        this.buttonType = type;
    }

    public void setOpacity(int opacity) {
        int hexOpacity = opacity * 255 / 100;
        this.opacity = opacity;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);
        invalidate();
    }
    public void setButtonData(JSONObject button_data) {
        this.buttonData = button_data;
    }

    public void setHide(Boolean isHide) {
        this.isHide = isHide;
        if (isHide) {
            this.setVisibility(GONE);
        }else {
            this.setVisibility(VISIBLE);
        }
        invalidate();
    }

    public void setGroup(int group) {
        this.group = group;
        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }


    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("TEXT", text);
        configuration.put("ICON", icon);
        configuration.put("LAYER", layer);
        configuration.put("VK_CODE", vk_code);
        configuration.put("RADIUS", radius);
        configuration.put("OPACITY", opacity);
        configuration.put("NORMAL_COLOR", normalColor);
        configuration.put("PRESSED_COLOR", pressedColor);
        configuration.put("TYPE", buttonType.toString());
        configuration.put("BUTTON_DATA", buttonData);
        configuration.put("IS_HIDE", isHide);
        configuration.put("GROUP", group);

        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = configuration.getInt("LEFT");
        layoutParams.topMargin = configuration.getInt("TOP");
        layoutParams.width = configuration.getInt("WIDTH");
        layoutParams.height = configuration.getInt("HEIGHT");
        setText(configuration.getString("TEXT"));
        setIcon(configuration.getInt("ICON"));
        setLayer(configuration.getInt("LAYER"));
        setVkCode(configuration.getString("VK_CODE"));
        setRadius(configuration.getInt("RADIUS"));
        setOpacity(configuration.getInt("OPACITY"));
        setColors(configuration.getInt("NORMAL_COLOR"), configuration.getInt("PRESSED_COLOR"));
        setType(ButtonType.valueOf(configuration.getString("TYPE")));
        setButtonData(configuration.getJSONObject("BUTTON_DATA"));

        try {
            setHide(configuration.getBoolean("IS_HIDE"));
            setGroup(configuration.getInt("GROUP"));
            Log.d("heokami", "loadConfiguration -> "+ elementId + " isHide:" + isHide + " group:" + group);
            // 等待布局完成恢复隐藏
            ViewTreeObserver vto = getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    setVisibility(isHide ? GONE : VISIBLE);
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
        }

        requestLayout();
    }
}
