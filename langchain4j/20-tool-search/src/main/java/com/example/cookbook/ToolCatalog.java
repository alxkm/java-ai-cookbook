package com.example.cookbook;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Twenty tools across four domains, which is where the problem this recipe solves starts.
 *
 * Every tool definition - name, description, parameter schema - is prompt text, sent on every
 * request and paid for on every request. Twenty is already enough to notice; the internal API
 * surface of a real company is hundreds, and sending all of them costs money on every turn and
 * measurably hurts tool selection, because the model is choosing from a longer list of things
 * that look alike.
 *
 * The descriptions are written to be searchable, not pretty: the words a user would actually use
 * are in them, because that is what the index matches on.
 */
public class ToolCatalog {

    // ---- orders -------------------------------------------------------------------------------

    @Tool("Look up the current delivery status of an order by its id.")
    String orderStatus(@P("Order id, like A-1001") String orderId) {
        return "shipped";
    }

    @Tool("Cancel an order that has not shipped yet.")
    String cancelOrder(@P("Order id") String orderId) {
        return "cancelled";
    }

    @Tool("List the line items and quantities on an order.")
    String orderItems(@P("Order id") String orderId) {
        return "2 x widget, 1 x gasket";
    }

    @Tool("Change the delivery address on an order that has not shipped.")
    String updateOrderAddress(@P("Order id") String orderId,
                              @P("New postal address") String address) {
        return "address updated";
    }

    @Tool("Find recent orders placed by a customer.")
    String ordersForCustomer(@P("Customer id") String customerId) {
        return "A-1001, A-1042";
    }

    // ---- billing ------------------------------------------------------------------------------

    @Tool("Issue a refund for an order, in full or in part.")
    String refundOrder(@P("Order id") String orderId,
                       @P("Amount to refund in cents") int amountCents) {
        return "refund issued";
    }

    @Tool("Fetch an invoice and its payment status.")
    String invoice(@P("Invoice number") String invoiceNumber) {
        return "paid";
    }

    @Tool("Apply a discount code to an unpaid invoice.")
    String applyDiscount(@P("Invoice number") String invoiceNumber,
                         @P("Discount code") String code) {
        return "discount applied";
    }

    @Tool("Charge a saved payment method for an outstanding balance.")
    String chargeBalance(@P("Customer id") String customerId) {
        return "charged";
    }

    @Tool("Explain why a payment was declined by the processor.")
    String declineReason(@P("Payment id") String paymentId) {
        return "insufficient funds";
    }

    // ---- shipping -----------------------------------------------------------------------------

    @Tool("Track a parcel with the carrier and return its last scan.")
    String trackParcel(@P("Tracking number") String trackingNumber) {
        return "out for delivery";
    }

    @Tool("Book a courier collection for a return.")
    String bookCollection(@P("Order id") String orderId) {
        return "collection booked";
    }

    @Tool("Print a prepaid return shipping label.")
    String returnLabel(@P("Order id") String orderId) {
        return "label-9912.pdf";
    }

    @Tool("Estimate a delivery date for a postcode and shipping class.")
    String estimateDelivery(@P("Destination postcode") String postcode,
                            @P("Shipping class") String shippingClass) {
        return "Thursday";
    }

    @Tool("List the carriers that serve a destination country.")
    String carriersFor(@P("Country code") String country) {
        return "DHL, UPS";
    }

    // ---- inventory ----------------------------------------------------------------------------

    @Tool("Check how many units of a product are in stock.")
    String stockLevel(@P("Product sku") String sku) {
        return "42";
    }

    @Tool("Reserve stock for an order so it cannot be sold twice.")
    String reserveStock(@P("Product sku") String sku,
                        @P("Quantity") int quantity) {
        return "reserved";
    }

    @Tool("Find the warehouse a product ships from.")
    String warehouseFor(@P("Product sku") String sku) {
        return "LEJ-2";
    }

    @Tool("Report when a discontinued product was last available.")
    String discontinuedOn(@P("Product sku") String sku) {
        return "2026-03-14";
    }

    @Tool("Suggest a replacement product for one that is out of stock.")
    String replacementFor(@P("Product sku") String sku) {
        return "WID-2210";
    }
}
