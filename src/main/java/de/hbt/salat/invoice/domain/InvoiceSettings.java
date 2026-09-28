package de.hbt.salat.invoice.domain;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import de.hbt.salat.common.domain.AuditedEntity;

@Builder
@Getter
public class InvoiceSettings extends AuditedEntity {

  public enum ImageUrl { LOGO , CLAIM }

  private final String name;
  private final Map<ImageUrl, String> imageUrls;
  private final String customCss;

  public String getImageUrl(ImageUrl imageUrl) {
    if (imageUrls == null || !imageUrls.containsKey(imageUrl)) {
      return null;
    }
    return imageUrls.get(imageUrl);
  }

}