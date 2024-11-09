package org.tb.invoice.service;

import static java.util.Map.of;
import static org.tb.invoice.domain.InvoiceSettings.ImageUrl.CLAIM;
import static org.tb.invoice.domain.InvoiceSettings.ImageUrl.LOGO;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.tb.invoice.domain.InvoiceSettings;

@Service
public class InvoiceSettingsService {

  private final List<InvoiceSettings> repository = new ArrayList<>();

  public InvoiceSettingsService() {
    repository.add(InvoiceSettings.builder()
      .name("NestorIT")
      .customCss("""
          html { 
              -webkit-print-color-adjust: exact; 
          }
          .hbt_claim {
              display: none;
          }
          .hbt_logo {
              height: 2.5cm;
          }
          .invoice_suborder_row {
              background-color: #dbfb58;
              border-bottom: 3px solid #fd9109;
          }
          """)
      .imageUrls(of(
        LOGO, "/images/NestorIT-Logo.jpg",
        CLAIM, "/images/disk.png"
      )).build()
    );
    repository.add(InvoiceSettings.builder()
        .name("HBT")
        .customCss("""
          
          """)
        .imageUrls(of(
            LOGO, "/images/HBT_Logo_RGB_positiv.svg",
            CLAIM, "/images/HBT_Claim_RGB_positiv.svg"
        )).build()
    );
  }

  public List<InvoiceSettings> getAllSettings() {
    return new ArrayList<>(repository);
  }

}
