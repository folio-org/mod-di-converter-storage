INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) VALUES

('e5cb0d57-51ca-4211-93af-ce5b4b08a420', '{
  "id": "e5cb0d57-51ca-4211-93af-ce5b4b08a420",
  "name": "Default - Mosaic invoice",
  "incomingRecordType": "EDIFACT_INVOICE",
  "existingRecordType": "INVOICE",
  "hidden" : false,
  "description": "Default EDIFACT invoice field mapping profile for Mosaic. Edit to add details specific to your library and invoices. If additional Mosaic invoice profiles are needed, duplicate this one.",
  "metadata": {
    "createdDate": "2025-09-04T15:00:00.000",
    "updatedDate": "2025-09-04T16:00:00.462+0000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  },
  "userInfo": {
    "lastName": "System",
    "userName": "System",
    "firstName": "System"
  },
  "mappingDetails": {
    "name": "invoice",
    "recordType": "INVOICE",
    "mappingFields": [
      {
        "name": "invoiceDate",
        "enabled": true,
        "path": "invoice.invoiceDate",
        "value": "DTM+137[2]",
        "subfields": []
      },
      {
        "name": "status",
        "enabled": true,
        "path": "invoice.status",
        "value": "\"Open\"",
        "subfields": []
      },
      {
        "name": "paymentDue",
        "enabled": true,
        "path": "invoice.paymentDue",
        "value": "",
        "subfields": []
      },
      {
        "name": "paymentTerms",
        "enabled": true,
        "path": "invoice.paymentTerms",
        "value": "",
        "subfields": []
      },
      {
        "name": "approvalDate",
        "enabled": false,
        "path": "invoice.approvalDate",
        "value": "",
        "subfields": []
      },
      {
        "name": "approvedBy",
        "enabled": false,
        "path": "invoice.approvedBy",
        "value": "",
        "subfields": []
      },
      {
        "name": "acqUnitIds",
        "enabled": true,
        "path": "invoice.acqUnitIds[]",
        "value": "",
        "subfields": [{
          "order": 0,
          "path": "invoice.acqUnitIds[]",
          "fields": [{
            "name": "acqUnitIds",
            "enabled": true,
            "path": "invoice.acqUnitIds[]",
            "value": "",
            "subfields": []
          }]
        }]
      },
      {
        "name": "billTo",
        "enabled": true,
        "path": "invoice.billTo",
        "value": "",
        "subfields": [],
        "acceptedValues": {}
      },
      {
        "name": "billToAddress",
        "enabled": false,
        "path": "invoice.billToAddress",
        "value": "",
        "subfields": []
      },
      {
        "name": "batchGroupId",
        "enabled": true,
        "path": "invoice.batchGroupId",
        "value": "",
        "subfields": []
      },
      {
        "name": "subTotal",
        "enabled": false,
        "path": "invoice.subTotal",
        "value": "",
        "subfields": []
      },
      {
        "name": "adjustmentsTotal",
        "enabled": false,
        "path": "invoice.adjustmentsTotal",
        "value": "",
        "subfields": []
      },
      {
        "name": "total",
        "enabled": false,
        "path": "invoice.total",
        "value": "",
        "subfields": []
      },
      {
        "name": "lockTotal",
        "enabled": true,
        "path": "invoice.lockTotal",
        "value": "MOA+9[2]",
        "subfields": []
      },
      {
        "name": "note",
        "enabled": true,
        "path": "invoice.note",
        "value": "",
        "subfields": []
      },
      {
        "name": "adjustments",
        "enabled": true,
        "path": "invoice.adjustments[]",
        "value": "",
        "subfields": []
      },
      {
        "name": "vendorInvoiceNo",
        "enabled": true,
        "path": "invoice.vendorInvoiceNo",
        "subfields": [],
        "value": "BGM+380+[1]"
      },
      {
        "name": "vendorId",
        "enabled": true,
        "path": "invoice.vendorId",
        "value": "",
        "subfields": []
      },
      {
        "name": "accountingCode",
        "enabled": true,
        "path": "invoice.accountingCode",
        "value": "",
        "subfields": []
      },
      {
        "name" : "accountNo",
        "enabled" : "false",
        "required" : false,
        "path" : "invoice.accountNo",
        "value" : "",
        "subfields" : [ ]
      },
      {
        "name": "folioInvoiceNo",
        "enabled": false,
        "path": "invoice.folioInvoiceNo",
        "value": "",
        "subfields": []
      },
      {
        "name": "paymentMethod",
        "enabled": true,
        "path": "invoice.paymentMethod",
        "value": "",
        "subfields": []
      },
      {
        "name": "chkSubscriptionOverlap",
        "enabled": true,
        "path": "invoice.chkSubscriptionOverlap",
        "booleanFieldAction": "ALL_FALSE",
        "subfields": []
      },
      {
        "name": "exportToAccounting",
        "enabled": true,
        "path": "invoice.exportToAccounting",
        "booleanFieldAction": "ALL_TRUE",
        "subfields": []
      },
      {
        "name": "currency",
        "enabled": true,
        "path": "invoice.currency",
        "subfields": [],
        "value": "CUX+2[2]"
      },
      {
        "name": "currentExchangeRate",
        "enabled": false,
        "path": "invoice.currentExchangeRate",
        "value": "",
        "subfields": []
      },
      {
        "name": "exchangeRate",
        "enabled": true,
        "path": "invoice.exchangeRate",
        "value": "",
        "subfields": []
      },
      {
        "name": "invoiceLines",
        "enabled": true,
        "path": "invoice.invoiceLines[]",
        "value": "",
        "repeatableFieldAction": "EXTEND_EXISTING",
        "subfields": [
          {
            "order": 0,
            "path": "invoice.invoiceLines[]",
            "fields": [
              {
                "name": "description",
                "enabled": true,
                "path": "invoice.invoiceLines[].description",
                "value": "{POL_title}; else IMD+L+050+[4-5]",
                "subfields": []
              },
              {
                "name": "poLineId",
                "enabled": true,
                "path": "invoice.invoiceLines[].poLineId",
                "value": "RFF+LI[2]",
                "subfields": []
              },
              {
                "name": "invoiceLineNumber",
                "enabled": false,
                "path": "invoice.invoiceLines[].invoiceLineNumber",
                "value": "",
                "subfields": []
              },
              {
                "name": "invoiceLineStatus",
                "enabled": false,
                "path": "invoice.invoiceLines[].invoiceLineStatus",
                "value": "",
                "subfields": []
              },
              {
                "name" : "referenceNumbers",
                "enabled" : true,
                "path" : "invoice.invoiceLines[].referenceNumbers[]",
                "value" : "",
                "repeatableFieldAction" : "EXTEND_EXISTING",
                "subfields" : [ {
                  "order" : 0,
                  "path" : "invoice.invoiceLines[].referenceNumbers[]",
                  "fields" : [ {
                    "name" : "refNumber",
                    "enabled" : true,
                    "path" : "invoice.invoiceLines[].referenceNumbers[].refNumber",
                    "value" : "RFF+SLI[2]",
                    "subfields" : [ ]
                  }, {
                    "name" : "refNumberType",
                    "enabled" : true,
                    "path" : "invoice.invoiceLines[].referenceNumbers[].refNumberType",
                    "value" : "\"Vendor order reference number\"",
                    "subfields" : [ ]
                  } ]
                } ]
              },
              {
                "name": "subscriptionInfo",
                "enabled": true,
                "path": "invoice.invoiceLines[].subscriptionInfo",
                "value": "",
                "subfields": []
              },
              {
                "name": "subscriptionStart",
                "enabled": true,
                "path": "invoice.invoiceLines[].subscriptionStart",
                "value": "",
                "subfields": []
              },
              {
                "name": "subscriptionEnd",
                "enabled": true,
                "path": "invoice.invoiceLines[].subscriptionEnd",
                "value": "",
                "subfields": []
              },
              {
                "name": "comment",
                "enabled": true,
                "path": "invoice.invoiceLines[].comment",
                "value": "",
                "subfields": []
              },
              {
                "name": "lineAccountingCode",
                "enabled": false,
                "path": "invoice.invoiceLines[].accountingCode",
                "value": "",
                "subfields": []
              },
              {
                "name": "accountNumber",
                "enabled": true,
                "path": "invoice.invoiceLines[].accountNumber",
                "value": "",
                "subfields": []
              },
              {
                "name": "quantity",
                "enabled": true,
                "path": "invoice.invoiceLines[].quantity",
                "value": "QTY+47[2]",
                "subfields": []
              },
              {
                "name": "lineSubTotal",
                "enabled": true,
                "path": "invoice.invoiceLines[].subTotal",
                "value": "MOA+203[2]",
                "subfields": []
              },
              {
                "name": "releaseEncumbrance",
                "enabled": true,
                "path": "invoice.invoiceLines[].releaseEncumbrance",
                "booleanFieldAction": "ALL_TRUE",
                "subfields": []
              },
              {
                "name": "fundDistributions",
                "enabled": true,
                "path": "invoice.invoiceLines[].fundDistributions[]",
                "value": "{POL_FUND_DISTRIBUTIONS}",
                "subfields": []
              },
              {
                "name": "lineAdjustments",
                "enabled": true,
                "path": "invoice.invoiceLines[].adjustments[]",
                "value": "",
                "subfields": []
              }
            ]
          }
        ]
      }
    ]
  }
}')

ON CONFLICT DO NOTHING;