package it.dhd.oxygencustomizer.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

import it.dhd.oxygencustomizer.IRootProviderProxy;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.utils.BitmapSubjectSegmenter;

public class RootProviderProxy extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new RootPoviderProxyIPC(this);
    }

    class RootPoviderProxyIPC extends IRootProviderProxy.Stub
    {
        /** @noinspection unused*/
        String TAG = getClass().getSimpleName();

        private final List<String> rootAllowedPacks;
        private final boolean rootGranted;

        private RootPoviderProxyIPC(Context context)
        {
            try {
                Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER));
            }
            catch (Throwable ignored){}
            rootGranted = Shell.getShell().isRoot();

            rootAllowedPacks = Arrays.asList(context.getResources().getStringArray(R.array.root_requirement));
        }

        /** @noinspection RedundantThrows*/
        @Override
        public String[] runCommand(String command) throws RemoteException {
            try {
                ensureEnvironment();

                List<String> result = Shell.cmd(command).exec().getOut();
                return result.toArray(new String[0]);
            }
            catch (Throwable t)
            {
                return new String[0];
            }
        }

        @Override
        public void extractSubject(Bitmap input, String resultPath) throws RemoteException {
            ensureEnvironment();

            try {
                new BitmapSubjectSegmenter(getApplicationContext()).segmentSubject(input, new BitmapSubjectSegmenter.SegmentResultListener() {
                    @Override
                    public void onSuccess(Bitmap result) {
                        try {
                            File tempFile = File.createTempFile("lswt", ".png");

                            Log.d(TAG,"DepthWallpaper extractSubject: " + tempFile.getAbsolutePath() + " -> " + resultPath);

                            FileOutputStream outputStream = new FileOutputStream(tempFile);
                            result.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

                            outputStream.close();
                            result.recycle();

                            Shell.cmd("cp -F " + tempFile.getAbsolutePath() + " " + resultPath).exec();
                            Shell.cmd("chmod 644 " + resultPath).exec();
                            Log.d(TAG, "DepthWallpaper onSuccess: BitmapSubjectSegmenter " + resultPath);
                        } catch (Throwable t) {
                            Log.e(TAG, "onSuccess: BitmapSubjectSegmenter", t);
                        }
                    }

                    @Override
                    public void onFail() {
                        Log.d(TAG, "onFail: BitmapSubjectSegmenter");
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "extractSubject: BitmapSubjectSegmenter", t);
            }
        }

        private void ensureEnvironment() throws RemoteException {
            if(!rootGranted)
            {
                throw new RemoteException("Root permission denied");
            }

            ensureSecurity(Binder.getCallingUid());
        }

        private void ensureSecurity(int uid) throws RemoteException {
            for (String packageName : getPackageManager().getPackagesForUid(uid)) {
                if(rootAllowedPacks.contains(packageName))
                    return;
            }
            throw new RemoteException("You do know you're not supposed to use this service. So...");
        }
    }
}