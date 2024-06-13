package com.sourcegraph.cody.autocomplete;

import com.sourcegraph.cody.agent.protocol.Position;
import com.sourcegraph.cody.agent.protocol.Range;
import com.sourcegraph.cody.vscode.TextDocument;
import java.net.URI;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestTextDocument implements TextDocument {
  @NotNull private final URI uri;
  @NotNull private final String fileName;
  @NotNull private final String text;

  @Nullable private final String languageId;

  public TestTextDocument(
      @NotNull URI uri,
      @NotNull String fileName,
      @NotNull String text,
      @Nullable String languageId) {
    this.uri = uri;
    this.fileName = fileName;
    this.text = text;
    this.languageId = languageId;
  }

  @Override
  public URI uri() {
    return this.uri;
  }

  @Override
  public @NotNull String fileName() {
    return this.fileName;
  }

  @Override
  public int offsetAt(Position position) {
    return 0;
  }

  @Override
  public @NotNull String getText() {
    return this.text;
  }

  @Override
  public String getText(Range range) {
    return null;
  }

  @Override
  public Position positionAt(int offset) {
    return new Position(0, 0);
  }

  @Override
  public @NotNull Optional<String> getLanguageId() {
    return Optional.ofNullable(this.languageId);
  }
}
