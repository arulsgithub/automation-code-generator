package com.sample.tests;

import com.sample.pages.CartPage;
import com.sample.pages.CheckoutInfoPage;
import com.sample.pages.InventoryPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Generated from the recording session-20261107-091346.json.
 * Review it before you use it.
 */
class RecordedFlowTest extends BaseTest {

    @Test
    @DisplayName("Recorded flow")
    void recordedFlow() {
        // TODO: replace TODO_PASSWORD with real test data
        InventoryPage inventoryPage = loginPage.loginAs("standard_user", "TODO_PASSWORD");
        inventoryPage.addItemToCart("Sauce Labs Backpack");
        inventoryPage.addItemToCart("Sauce Labs Bike Light");
        inventoryPage.addItemToCart("Sauce Labs Fleece Jacket");
        inventoryPage.addItemToCart("Sauce Labs Bolt T Shirt");
        CartPage cartPage = inventoryPage.openCart();
        // NEW METHOD: CartPage.removeItem (code is in generated/additions/CartPage.txt)
        cartPage.removeItem("Sauce Labs Backpack");
        // NEW METHOD: CartPage.removeItem (code is in generated/additions/CartPage.txt)
        cartPage.removeItem("Sauce Labs Fleece Jacket");
        CheckoutInfoPage checkoutInfoPage = cartPage.checkout();
        // NEW METHOD: CheckoutInfoPage.clickCancel (code is in generated/additions/CheckoutInfoPage.txt)
        checkoutInfoPage.clickCancel();
    }
}
