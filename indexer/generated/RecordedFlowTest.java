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
        inventoryPage.addItemToCart("book");
        inventoryPage.addItemToCart("bag");
        inventoryPage.addItemToCart("pen");
        inventoryPage.addItemToCart("note");
        CartPage cartPage = inventoryPage.openCart();
        // NEW METHOD: add clickRemove(0 value(s)) to CartPage
        cartPage.clickRemove();
        // NEW METHOD: add clickRemove(0 value(s)) to CartPage
        cartPage.clickRemove();
        CheckoutInfoPage checkoutInfoPage = cartPage.checkout();
        // NEW METHOD: add clickCancel(0 value(s)) to CheckoutInfoPage
        checkoutInfoPage.clickCancel();
    }
}
