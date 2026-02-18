package com.limelight.grid;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.recyclerview.widget.RecyclerView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import com.limelight.preferences.PreferenceConfiguration;

public class PcRecyclerAdapter extends RecyclerView.Adapter<PcRecyclerAdapter.PcViewHolder> {
    
    private Context context;
    private List<PcView.ComputerObject> computerList;
    private Map<String, PairingState> pairingStates = new HashMap<>();
    private PcView pcView;
    private static final String PAIRING_TAG = "Pairing";
    private static final int SET_BITRATE_ID = 1001;
    
    public PcRecyclerAdapter(Context context) {
        this.context = context;
        this.computerList = new ArrayList<>();
        if (context instanceof PcView) {
            this.pcView = (PcView) context;
        }
    }
    
    public void setComputers(List<PcView.ComputerObject> computers) {
        this.computerList.clear();
        this.computerList.addAll(computers);
        sortList();
        notifyDataSetChanged();
    }
    
    public void addComputer(PcView.ComputerObject computer) {
        computerList.add(computer);
        sortList();
        notifyDataSetChanged();
    }
    
    public boolean removeComputer(PcView.ComputerObject computer) {
        boolean removed = computerList.remove(computer);
        if (removed) {
            notifyDataSetChanged();
        }
        return removed;
    }
    
    private void sortList() {
        Collections.sort(computerList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                int nameCmp = lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
                if (nameCmp != 0) {
                    return nameCmp;
                }

                if (lhs.address != null && rhs.address != null) {
                    return lhs.address.toString().compareTo(rhs.address.toString());
                }
                else if (lhs.address != null) {
                    return 1;
                }
                else if (rhs.address != null) {
                    return -1;
                }
                else {
                    return 0;
                }
            }
        });
    }
    
    // 配对状态管理方法
    public void startPairing(PcView.ComputerObject computer, String pin) {
        String key = getComputerKey(computer);
        pairingStates.put(key, new PairingState(PairingStatus.PAIRING, pin));
        notifyDataSetChanged();
    }
    
    public void updatePairingStatus(PcView.ComputerObject computer, PairingStatus status) {
        String key = getComputerKey(computer);
        PairingState currentState = pairingStates.get(key);
        if (currentState != null) {
            pairingStates.put(key, new PairingState(status, currentState.pin));
            notifyDataSetChanged();
        }
    }
    
    public void clearPairingStatus(PcView.ComputerObject computer) {
        String key = getComputerKey(computer);
        pairingStates.remove(key);
        notifyDataSetChanged();
    }
    
    public boolean isPairing(PcView.ComputerObject computer) {
        String key = getComputerKey(computer);
        PairingState state = pairingStates.get(key);
        return state != null && state.status == PairingStatus.PAIRING;
    }
    
    private String getComputerKey(PcView.ComputerObject computer) {
        return computer.details.uuid + "_" + (computer.address != null ? computer.address.toString() : "");
    }
    
    private boolean isPolling = true;

    public void setPolling(boolean polling) {
        this.isPolling = polling;
        notifyDataSetChanged();
    }

    @Override
    public PcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pc_grid_item, parent, false);
        return new PcViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(PcViewHolder holder, int position) {
        PcView.ComputerObject computer = computerList.get(position);
        holder.bind(computer);
    }
    
    @Override
    public int getItemCount() {
        return computerList.size();
    }
    
    public PcView.ComputerObject getItem(int position) {
        return computerList.get(position);
    }
    
    public class PcViewHolder extends RecyclerView.ViewHolder {
        private ImageView gridImage;
        private ImageView gridOverlay;
        private ProgressBar gridSpinner;
        private TextView gridText;
        private LinearLayout pairingInfoLayout;
        private TextView pairingStatusText;
        private Button cancelPairingButton;
        
        public PcViewHolder(View itemView) {
            super(itemView);
            gridImage = itemView.findViewById(R.id.grid_image);
            gridOverlay = itemView.findViewById(R.id.grid_overlay);
            gridSpinner = itemView.findViewById(R.id.grid_spinner);
            gridText = itemView.findViewById(R.id.grid_text);
            pairingInfoLayout = itemView.findViewById(R.id.pairing_info_layout);
            pairingStatusText = itemView.findViewById(R.id.pairing_status_text);
            cancelPairingButton = itemView.findViewById(R.id.cancel_pairing_button);
        }
        
        public void bind(PcView.ComputerObject computer) {
            gridImage.setImageResource(R.drawable.ic_computer);
            
            // 为每个item单独设置长按监听器，使用自定义弹出菜单
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Log.d("PcRecyclerAdapter", "Item长按事件触发，位置: " + getAdapterPosition());
                    // 使用自定义弹出菜单替代系统上下文菜单
                    if (context instanceof PcView) {
                        showCustomContextMenu(v, getAdapterPosition());
                    }
                    return true;
                }
            });
            
            // Dim the entire view if this specific address is not reachable
            if (computer.address != null && !computer.details.reachableAddresses.contains(computer.address)) {
                itemView.setAlpha(0.4f);
            } else {
                itemView.setAlpha(1.0f);
            }
            
            if (computer.details.state == ComputerDetails.State.UNKNOWN && isPolling) {
                gridSpinner.setVisibility(View.VISIBLE);
            } else {
                gridSpinner.setVisibility(View.INVISIBLE);
            }
            
            if (computer.address != null) {
                gridText.setText(computer.details.name + "\n" + computer.address.address);
            } else {
                gridText.setText(computer.details.name);
            }
            
            // 处理配对状态显示
            String key = getComputerKey(computer);
            PairingState pairingState = pairingStates.get(key);
            
            if (pairingState != null && pairingState.status == PairingStatus.PAIRING) {
                // 显示配对信息，带动画
                if (pairingInfoLayout.getVisibility() != View.VISIBLE) {
                    pairingInfoLayout.setAlpha(0f);
                    pairingInfoLayout.setVisibility(View.VISIBLE);
                    pairingInfoLayout.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start();
                }
                
                pairingStatusText.setText(String.format(itemView.getContext().getString(R.string.pairing_status_pairing), pairingState.pin));
                cancelPairingButton.setText(R.string.pairing_cancel_button);
                
                // 设置取消按钮点击事件
                final PcView.ComputerObject finalComputer = computer;
                cancelPairingButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (pcView != null) {
                            pcView.cancelPairing(finalComputer);
                        }
                    }
                });
                
                // 配对中时隐藏锁图标
                gridOverlay.setVisibility(View.GONE);
            } else {
                // 隐藏配对信息，带动画
                if (pairingInfoLayout.getVisibility() == View.VISIBLE) {
                    pairingInfoLayout.animate()
                        .alpha(0f)
                        .setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                pairingInfoLayout.setVisibility(View.GONE);
                            }
                        })
                        .start();
                } else {
                    pairingInfoLayout.setVisibility(View.GONE);
                }
                
                // 恢复正常的覆盖图标显示
                if (computer.details.state == ComputerDetails.State.OFFLINE) {
                    gridOverlay.setImageResource(R.drawable.ic_pc_offline);
                    gridOverlay.setVisibility(View.VISIBLE);
                } else if (computer.details.state == ComputerDetails.State.ONLINE &&
                        computer.details.pairState == PairingManager.PairState.NOT_PAIRED) {
                    gridOverlay.setImageResource(R.drawable.ic_lock);
                    gridOverlay.setAlpha(1.0f);
                    gridOverlay.setVisibility(View.VISIBLE);
                } else {
                    gridOverlay.setVisibility(View.GONE);
                }
            }
            
            // 设置点击事件
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (computer.details.state == ComputerDetails.State.OFFLINE ||
                        (computer.details.state == ComputerDetails.State.UNKNOWN && computer.address == null)) {
                        // Open the context menu if a PC is offline or refreshing (and we have no address to try)
                        itemView.showContextMenu();
                    } else if (computer.details.pairState != PairingManager.PairState.PAIRED) {
                        // Pair an unpaired machine by default
                        if (pcView != null) {
                            pcView.doPair(computer);
                        }
                    } else {
                        if (pcView != null) {
                            pcView.doAppList(computer, false, false);
                        }
                    }
                }
            });
            
        }
    }
    
    // 配对状态枚举
    public enum PairingStatus {
        PAIRING,
        SUCCESS,
        FAILED,
        CANCELLED
    }
    
    // 配对状态类
    private static class PairingState {
        PairingStatus status;
        String pin;
        
        PairingState(PairingStatus status, String pin) {
            this.status = status;
            this.pin = pin;
        }
    }
    
    // 显示自定义上下文菜单
    private void showCustomContextMenu(View anchorView, int position) {
        Log.d("PcRecyclerAdapter", "显示自定义上下文菜单，位置: " + position);
        
        if (pcView == null || position < 0 || position >= computerList.size()) {
            Log.w("PcRecyclerAdapter", "无法显示菜单: pcView=" + pcView + ", position=" + position + ", listSize=" + computerList.size());
            return;
        }
        
        PcView.ComputerObject computer = computerList.get(position);
        if (computer == null) {
            Log.w("PcRecyclerAdapter", "无法获取计算机对象");
            return;
        }
        
        // 创建弹出菜单
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(context, anchorView);
        android.view.Menu menu = popupMenu.getMenu();
        
        // 添加菜单项 - 复制原有的菜单逻辑
        // PopupMenu不支持setHeaderTitle，我们跳过标题设置
        
        // 根据计算机状态添加菜单项
        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(android.view.Menu.NONE, PcView.WOL_ID, 1,
                    context.getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(android.view.Menu.NONE, PcView.GAMESTREAM_EOL_ID, 2,
                    context.getResources().getString(R.string.pcview_menu_eol));
        }
        else if (computer.details.pairState != PairingManager.PairState.PAIRED) {
            menu.add(android.view.Menu.NONE, PcView.PAIR_ID, 1,
                    context.getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(android.view.Menu.NONE, PcView.GAMESTREAM_EOL_ID, 2,
                        context.getResources().getString(R.string.pcview_menu_eol));
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(android.view.Menu.NONE, PcView.RESUME_ID, 1,
                        context.getResources().getString(R.string.applist_menu_resume));
                menu.add(android.view.Menu.NONE, PcView.QUIT_ID, 2,
                        context.getResources().getString(R.string.applist_menu_quit));
            }
            
            if (computer.details.nvidiaServer) {
                menu.add(android.view.Menu.NONE, PcView.GAMESTREAM_EOL_ID, 3,
                        context.getResources().getString(R.string.pcview_menu_eol));
            }
            
            menu.add(android.view.Menu.NONE, PcView.FULL_APP_LIST_ID, 4,
                    context.getResources().getString(R.string.pcview_menu_app_list));
        }
        
        menu.add(android.view.Menu.NONE, PcView.TEST_NETWORK_ID, 5,
                context.getResources().getString(R.string.pcview_menu_test_network));
        if (computer.address != null && computer.details.manualAddresses.contains(computer.address)) {
            menu.add(android.view.Menu.NONE, PcView.DELETE_IP_ID, 6,
                    context.getResources().getString(R.string.pc_view_delete_ip));
        }
        menu.add(android.view.Menu.NONE, PcView.DELETE_ID, 7,
                context.getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(android.view.Menu.NONE, PcView.VIEW_DETAILS_ID, 8,
                context.getResources().getString(R.string.pcview_menu_details));
        
        // Add Set Bitrate item
        menu.add(android.view.Menu.NONE, SET_BITRATE_ID, 9,
                context.getResources().getString(R.string.pcview_menu_set_bitrate));
        
        // 设置菜单项点击监听器
        popupMenu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                Log.d("PcRecyclerAdapter", "菜单项点击: " + item.getItemId());
                if (item.getItemId() == SET_BITRATE_ID) {
                    showBitrateDialog(computer);
                    return true;
                }
                return pcView.onContextItemSelected(item, computer);
            }
        });
        
        // 显示菜单
        popupMenu.show();
        Log.d("PcRecyclerAdapter", "自定义上下文菜单已显示");
    }

    private void showBitrateDialog(final PcView.ComputerObject computer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(String.format(context.getString(R.string.dialog_title_set_bitrate), computer.details.name));
        // Remove message as it's self-explanatory with the slider/text
        // builder.setMessage(R.string.dialog_message_set_bitrate);

        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_seekbar, null);
        builder.setView(dialogView);

        final TextView valueText = dialogView.findViewById(R.id.bitrate_value);
        final SeekBar seekBar = dialogView.findViewById(R.id.bitrate_seekbar);

        // Bitrate constants (in Mbps for the slider)
        final int MIN_BITRATE_MBPS = 1; // 1 Mbps (slider min is 0 which we use for "Default")
        final int MAX_BITRATE_MBPS = 150; // 150 Mbps max
        final int STEP_SIZE_MBPS = 1;

        // Get current bitrate (stored in Kbps)
        int currentBitrateKbps = PreferenceConfiguration.getDeviceBitrate(context, computer.details.uuid, computer.address.address);
        int currentBitrateMbps = 0;

        if (currentBitrateKbps > 0) {
            currentBitrateMbps = currentBitrateKbps / 1000;
        }

        // Configure SeekBar
        // Range: 0 (Default) to 150 Mbps
        seekBar.setMax(MAX_BITRATE_MBPS);
        
        // Update display text logic
        final Runnable updateText = new Runnable() {
            @Override
            public void run() {
                int progress = seekBar.getProgress();
                if (progress == 0) {
                    // Get global default for hint, if possible (optional, keeping it simple for now)
                    valueText.setText("Default"); 
                    // Or fetch global: PreferenceConfiguration.readPreferences(context).bitrate / 1000 + " Mbps (Default)" 
                    // But that requires reading prefs which might be heavy here. 
                    // Let's stick to "Default" or "Use Global Setting"
                    valueText.setText(context.getString(R.string.default_val)); 
                } else {
                    valueText.setText(progress + " Mbps");
                }
            }
        };

        seekBar.setProgress(currentBitrateMbps);
        updateText.run();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateText.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        builder.setPositiveButton(R.string.intro_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int bitrateMbps = seekBar.getProgress();

                // First, always clear the address-specific bitrate to ensure we don't have stale overrides
                // 0 means remove in setDeviceBitrate
                PreferenceConfiguration.setDeviceBitrate(context, computer.details.uuid, computer.address.address, 0);

                // Then set the UUID-based bitrate (or remove it if 0)
                // We pass null for address to target the UUID-only key
                if (bitrateMbps > 0) {
                    PreferenceConfiguration.setDeviceBitrate(context, computer.details.uuid, null, bitrateMbps * 1000);
                } else {
                    PreferenceConfiguration.setDeviceBitrate(context, computer.details.uuid, null, 0);
                }
            }
        });
        builder.setNegativeButton(R.string.intro_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
}