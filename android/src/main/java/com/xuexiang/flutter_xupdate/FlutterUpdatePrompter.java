/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.flutter_xupdate;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.xuexiang.xupdate.entity.PromptEntity;
import com.xuexiang.xupdate.entity.UpdateEntity;
import com.xuexiang.xupdate.logs.UpdateLog;
import com.xuexiang.xupdate.proxy.IUpdatePrompter;
import com.xuexiang.xupdate.proxy.IUpdateProxy;
import com.xuexiang.xupdate.proxy.impl.DefaultPrompterProxyImpl;
import com.xuexiang.xupdate.service.OnFileDownloadListener;
import com.xuexiang.xupdate.utils.UpdateUtils;
import com.xuexiang.xupdate.widget.UpdateDialog;
import com.xuexiang.xupdate.widget.UpdateDialogActivity;
import com.xuexiang.xupdate.widget.UpdateDialogFragment;

import java.io.File;
import java.util.Locale;

/**
 * 默认的更新提示器
 *
 * @author xuexiang
 * @since 2018/7/2 下午4:05
 */
public class FlutterUpdatePrompter implements IUpdatePrompter {

    String locale;
    int theme;

    public FlutterUpdatePrompter() {
        this.locale = "";
        this.theme = 0;
    }

    public FlutterUpdatePrompter(String locale, int theme) {
        this.locale = locale;
        this.theme = theme;
    }

    @NonNull
    Resources getLocalizedResources(Context context, Locale desiredLocale) {
        if (Build.VERSION.SDK_INT >= 17) {
            Configuration conf = context.getResources().getConfiguration();
            conf = new Configuration(conf);
            conf.setLocale(desiredLocale);
            Context localizedContext = context.createConfigurationContext(conf);
            return localizedContext.getResources();
        }
        return context.getResources();
    }

    /**
     * 显示自定义提示
     *
     * @param updateEntity
     * @param updateProxy
     */
    private void showUpdatePrompt(final @NonNull UpdateEntity updateEntity, final @NonNull IUpdateProxy updateProxy) {
        int themeResId = 0;
        if (this.theme == 1) {
            themeResId = Build.VERSION.SDK_INT >= 23 ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
        } else if (this.theme == 2) {
            themeResId = Build.VERSION.SDK_INT >= 23 ? android.R.style.Theme_DeviceDefault_Dialog_Alert : AlertDialog.THEME_DEVICE_DEFAULT_DARK;
        }

        final Resources localeResources;
        if (locale.equals("")) {
            localeResources = updateProxy.getContext().getResources();
        } else {
            localeResources = getLocalizedResources(updateProxy.getContext(), new Locale(locale));
        }

        String targetSize = byte2FitMemorySize(updateEntity.getSize() * 1024);
        final String updateContent = updateEntity.getUpdateContent();

        String updateInfo = "";
        if (!TextUtils.isEmpty(targetSize)) {
            updateInfo = localeResources.getString(R.string.xupdate_lab_new_version_size) + targetSize + "\n";
        }
        if (!TextUtils.isEmpty(updateContent)) {
            updateInfo += updateContent;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(updateProxy.getContext(), themeResId);
        final int finalThemeResId = themeResId;
        builder.setTitle(String.format(localeResources.getString(R.string.xupdate_lab_ready_update), updateEntity.getVersionName()))
                .setMessage(updateInfo)
                .setPositiveButton(localeResources.getString(R.string.xupdate_lab_install), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updateProxy.startDownload(updateEntity, new OnFileDownloadListener() {
                            @Override
                            public void onStart() {
                                HProgressDialogUtils.showHorizontalProgressDialog(updateProxy.getContext(), localeResources.getString(R.string.xupdate_lab_downloading), false, finalThemeResId);
                            }

                            @Override
                            public void onProgress(float progress, long total) {
                                HProgressDialogUtils.setProgress(Math.round(progress * 100));
                            }

                            @Override
                            public boolean onCompleted(File file) {
                                HProgressDialogUtils.cancel();
                                return true;
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                HProgressDialogUtils.cancel();
                            }
                        });
                    }
                });
        if (updateEntity.isIgnorable()) {
            builder.setNegativeButton(localeResources.getString(R.string.xupdate_lab_ignore), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    UpdateUtils.saveIgnoreVersion(updateProxy.getContext(), updateEntity.getVersionName());
                }
            }).setCancelable(true);
        } else if (updateEntity.isForce())  {
            builder.setCancelable(false);
        } else {
            builder.setNegativeButton("Later", null).setCancelable(true);
        }
        builder.create().show();
    }

    /**
     * 显示版本更新提示
     *
     * @param updateEntity 更新信息
     * @param updateProxy  更新代理
     * @param promptEntity 提示界面参数
     */
    @Override
    public void showPrompt(@NonNull UpdateEntity updateEntity, @NonNull IUpdateProxy updateProxy, @NonNull PromptEntity promptEntity) {
        showUpdatePrompt(updateEntity, updateProxy);
    }

    @SuppressLint("DefaultLocale")
    private String byte2FitMemorySize(final long byteNum) {
        if (byteNum <= 0) {
            return "";
        } else if (byteNum < 1024) {
            return String.format("%.1fB", (double) byteNum);
        } else if (byteNum < 1048576) {
            return String.format("%.1fKB", (double) byteNum / 1024);
        } else if (byteNum < 1073741824) {
            return String.format("%.1fMB", (double) byteNum / 1048576);
        } else {
            return String.format("%.1fGB", (double) byteNum / 1073741824);
        }
    }
}
