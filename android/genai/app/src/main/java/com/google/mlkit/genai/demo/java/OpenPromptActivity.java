/*
 * Copyright 2025 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.genai.demo.java;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.ContentAdapter;
import com.google.mlkit.genai.demo.ContentItem;
import com.google.mlkit.genai.demo.GenerationConfigDialog;
import com.google.mlkit.genai.demo.GenerationConfigUtils;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.CountTokensResponse;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ImagePart;
import com.google.mlkit.genai.prompt.PromptPrefix;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity that demonstrates a chat-like interface for the Open Prompt API in Java, allowing
 * requests with both text and multiple images, and including generation configuration.
 */
public class OpenPromptActivity extends BaseActivity<ContentItem>
    implements GenerationConfigDialog.OnConfigUpdateListener {

  private static final String TAG = OpenPromptActivity.class.getSimpleName();
  private static final int ACTION_CLEAR_CACHES = 1000;

  private GenerativeModelFutures generativeModelFutures;
  private EditText requestEditText;
  private Button sendButton;
  private ImageButton selectImageButton;
  private ImageView imagePreview;
  private Button configButton;
  private EditText prefixEditText;

  @Nullable private Uri selectedImageUri = null;

  @Nullable private Float curTemperature = null;
  @Nullable private Integer curTopK = null;
  @Nullable private Integer curSeed = null;
  @Nullable private Integer curMaxOutputTokens = null;
  @Nullable private Integer curCandidateCount = null;
  private boolean useDefaultConfig = false;

  private final ActivityResultLauncher<String> pickImageLauncher =
      registerForActivityResult(
          new ActivityResultContracts.GetContent(),
          uri -> {
            if (uri != null) {
              selectedImageUri = uri;
              imagePreview.setImageURI(uri);
              imagePreview.setVisibility(View.VISIBLE);
              Toast.makeText(this, "1 image selected", Toast.LENGTH_SHORT).show();
            } else {
              Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            }
          });

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestEditText = findViewById(R.id.request_edit_text);
    sendButton = findViewById(R.id.send_button);
    selectImageButton = findViewById(R.id.select_image_prompt_button);
    imagePreview = findViewById(R.id.image_thumbnail_preview_input);
    configButton = findViewById(R.id.config_button);
    prefixEditText = findViewById(R.id.prefix_edit_text);

    selectImageButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

    // Remove the selected image when the user clicks on the image preview.
    imagePreview.setOnClickListener(
        v -> {
          selectedImageUri = null;
          imagePreview.setVisibility(View.GONE);
          Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show();
        });

    configButton.setOnClickListener(
        v -> new GenerationConfigDialog().show(getSupportFragmentManager(), null));

    sendButton.setOnClickListener(
        v -> {
          String requestText = requestEditText.getText().toString().trim();
          if (TextUtils.isEmpty(requestText)) {
            Toast.makeText(this, R.string.input_message_is_empty, Toast.LENGTH_SHORT).show();
            return;
          }

          String prefixText = prefixEditText.getText().toString().trim();
          if (!TextUtils.isEmpty(prefixText) && selectedImageUri != null) {
            Toast.makeText(this, R.string.warning_prefix_used_with_image, Toast.LENGTH_LONG).show();
            return;
          }

          ContentItem requestItem;
          if (selectedImageUri != null) {
            requestItem =
                new ContentItem.TextAndImagesItem(
                    requestText,
                    new ArrayList<>(ImmutableList.of(selectedImageUri)),
                    ContentAdapter.VIEW_TYPE_REQUEST_TEXT_AND_IMAGES);
          } else if (!TextUtils.isEmpty(prefixText)) {
            requestItem =
                ContentItem.TextWithPromptPrefixItem.Companion.fromRequest(prefixText, requestText);
          } else {
            requestItem = ContentItem.TextItem.Companion.fromRequest(requestText);
          }
          onSend(requestItem);
          requestEditText.setText("");
          imagePreview.setVisibility(View.GONE);
          selectedImageUri = null;
        });

    onConfigUpdated();

    initGenerator();
  }

  @Override
  public void onConfigUpdated() {
    useDefaultConfig = GenerationConfigUtils.getUseDefaultConfig(getApplicationContext());
    prefixEditText.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
    configButton.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
    selectImageButton.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
    imagePreview.setVisibility(
        useDefaultConfig || selectedImageUri == null ? View.GONE : View.VISIBLE);
    curTemperature = GenerationConfigUtils.getTemperature(getApplicationContext());
    curTopK = GenerationConfigUtils.getTopK(getApplicationContext());
    curSeed = GenerationConfigUtils.getSeed(getApplicationContext());
    curCandidateCount = GenerationConfigUtils.getCandidateCount(getApplicationContext());
    curMaxOutputTokens = GenerationConfigUtils.getMaxOutputTokens(getApplicationContext());
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_openprompt;
  }

  @Override
  protected ListenableFuture<List<String>> runInferenceImpl(
      ContentItem request, @Nullable StreamingCallback streamingCallback) {
    try {
      ListenableFuture<GenerateContentResponse> inferenceFuture;
      if (request instanceof ContentItem.TextItem textItem && useDefaultConfig) {
        // useDefaultConfig is used for the case where user wants to use utility function with
        // default config values
        inferenceFuture =
            (streamingCallback != null)
                ? generativeModelFutures.generateContent(textItem.getText(), streamingCallback)
                : generativeModelFutures.generateContent(textItem.getText());
      } else {
        GenerateContentRequest genRequest = buildGenerateContentRequest(request);
        inferenceFuture =
            (streamingCallback != null)
                ? generativeModelFutures.generateContent(genRequest, streamingCallback)
                : generativeModelFutures.generateContent(genRequest);
      }

      return Futures.transform(
          inferenceFuture,
          (GenerateContentResponse result) -> {
            if (result == null) {
              return ImmutableList.of();
            }

            ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<>();
            for (Candidate candidate : result.getCandidates()) {
              String text = candidate.getText();
              if (candidate.getFinishReason() == Candidate.FinishReason.MAX_TOKENS) {
                listBuilder.add(text + "\n(FinishReason: MAX_TOKENS)");
              } else {
                listBuilder.add(text);
              }
            }
            return listBuilder.build();
          },
          ContextCompat.getMainExecutor(this));
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  protected ListenableFuture<CountTokensResponse> countTokens(ContentItem request) {
    try {
      GenerateContentRequest genRequest = buildGenerateContentRequest(request);
      return generativeModelFutures.countTokens(genRequest);
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  protected ListenableFuture<Integer> getTokenLimit() {
    try {
      return generativeModelFutures.getTokenLimit();
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  private GenerateContentRequest buildGenerateContentRequest(ContentItem request) {
    String requestText = "";
    String promptPrefixText = "";
    Bitmap imageBitmap = null;
    if (request instanceof ContentItem.TextAndImagesItem tiRequest) {
      if (tiRequest.getText() != null) {
        requestText = tiRequest.getText();
      }
      for (Uri uri : tiRequest.getImageUris()) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
          imageBitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
          Log.e(TAG, "Error decoding image URI: " + uri, e);
        }
      }
    } else if (request instanceof ContentItem.TextItem textItem) {
      requestText = textItem.getText();
    } else if (request instanceof ContentItem.TextWithPromptPrefixItem textWithPromptPrefixItem) {
      requestText = textWithPromptPrefixItem.getDynamicSuffix();
      promptPrefixText = textWithPromptPrefixItem.getPromptPrefix();
    }

    GenerateContentRequest.Builder requestBuilder;

    if (imageBitmap != null) {
      requestBuilder =
          new GenerateContentRequest.Builder(new ImagePart(imageBitmap), new TextPart(requestText));
    } else {
      requestBuilder = new GenerateContentRequest.Builder(new TextPart(requestText));
      requestBuilder.setPromptPrefix(new PromptPrefix(promptPrefixText));
    }

    requestBuilder.setTemperature(curTemperature);
    requestBuilder.setTopK(curTopK);
    requestBuilder.setSeed(curSeed);
    requestBuilder.setMaxOutputTokens(curMaxOutputTokens);
    requestBuilder.setCandidateCount(curCandidateCount);

    return requestBuilder.build();
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    try {
      GenerateContentResponse result;
      if (useDefaultConfig) {
        // useDefaultConfig is used for the case where user wants to use utility function with
        // default config values
        result = generativeModelFutures.generateContent(request).get();
      } else {
        result =
            generativeModelFutures
                .generateContent(
                    buildGenerateContentRequest(
                        ContentItem.TextItem.Companion.fromRequest(request)))
                .get();
      }
      if (result.getCandidates().get(0).getText() != null) {
        return ImmutableList.of(result.getCandidates().get(0).getText());
      }
      return ImmutableList.of();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return ImmutableList.of("Failed to run inference: " + e.getMessage());
    }
  }

  @Override
  protected void startGeneratingUi() {
    super.startGeneratingUi();
    sendButton.setText(R.string.generating);
    sendButton.setEnabled(false);
    requestEditText.setEnabled(false);
    selectImageButton.setEnabled(false);
    configButton.setEnabled(false);
  }

  @Override
  protected void endGeneratingUi(String debugInfo) {
    super.endGeneratingUi(debugInfo);
    sendButton.setText(R.string.button_send);
    sendButton.setEnabled(true);
    requestEditText.setEnabled(true);
    selectImageButton.setEnabled(true);
    configButton.setEnabled(true);
  }

  private void initGenerator() {
    if (generativeModelFutures != null) {
      generativeModelFutures.getGenerativeModel().close();
    }
    GenerationConfig.Builder optionsBuilder = new GenerationConfig.Builder();

    GenerativeModel generativeModel = Generation.INSTANCE.getClient(optionsBuilder.build());
    this.generativeModelFutures = GenerativeModelFutures.from(generativeModel);
    resetProcessor();
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return generativeModelFutures.getBaseModelName();
  }

  @Override
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return generativeModelFutures.checkStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return generativeModelFutures.download(callback);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (!super.onCreateOptionsMenu(menu)) {
      return false;
    }
    menu.add(Menu.NONE, ACTION_CLEAR_CACHES, Menu.NONE, "Clear all prefix caches");
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_simple_api);
    if (item != null) {
      item.setVisible(true);
      item.setChecked(useDefaultConfig);
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == ACTION_CLEAR_CACHES) {
      Futures.addCallback(
          generativeModelFutures.clearCaches(),
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              Toast.makeText(OpenPromptActivity.this, "Caches cleared", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Throwable t) {
              Toast.makeText(OpenPromptActivity.this, "Failed to clear caches", Toast.LENGTH_SHORT)
                  .show();
            }
          },
          ContextCompat.getMainExecutor(this));
      return true;
    } else if (item.getItemId() == R.id.action_simple_api) {
      boolean newState = !item.isChecked();
      item.setChecked(newState);
      GenerationConfigUtils.setUseDefaultConfig(getApplicationContext(), newState);
      onConfigUpdated();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
