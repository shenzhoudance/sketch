/*
 * Copyright 2014 Peng fei Pan
 * Copyright 2013 Peng fei Pan
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.android.imageloader.task.assets;

import java.util.concurrent.locks.ReentrantLock;

import me.xiaopan.android.imageloader.Configuration;
import me.xiaopan.android.imageloader.decode.AssetsInputStreamCreator;
import me.xiaopan.android.imageloader.decode.InputStreamCreator;
import me.xiaopan.android.imageloader.task.BitmapLoadCallable;
import me.xiaopan.android.imageloader.task.Request;
import me.xiaopan.android.imageloader.util.Scheme;

public class AssetsBitmapLoadCallable extends BitmapLoadCallable {
	
	public AssetsBitmapLoadCallable(Request request, ReentrantLock reentrantLock, Configuration configuration) {
		super(request, reentrantLock, configuration);
	}

	@Override
	public InputStreamCreator getInputStreamCreator() {
		return new AssetsInputStreamCreator(configuration.getContext(), Scheme.ASSETS.crop(request.getImageUri()));
	}

	@Override
	public void onFailed() {
		
	}
}