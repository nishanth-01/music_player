package com.example.mediasession;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CompatMethods {
    //TODO : replce this with androidx.core.content.res.ResourcesCompat
    /** @param theme won't apply if api level is lesser than 21 **/
    public static Drawable getDrawable(
            @NonNull Resources resources, int resId, @Nullable Resources.Theme theme)
            throws Resources.NotFoundException {
        try{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                return resources.getDrawable(resId, theme);
            } else {
                return resources.getDrawable(resId);
            }
        } catch (Resources.NotFoundException e){
            throw e;
        }
    }
}
