Base URL: https://smgs.ams.slb.com/

Login
=====

1. Go to `${BASE_URL}sv/user-index.jsp`
2. Set cookies and follow redirect as per response
   (A `JSESSIONID` cookie is set)
3. Fill in form `frmMain` setting `j_username` and `j_password`
   (this submits to `${BASE_URL}sv/mfgx/j_security_check`)
4. Will be redirected back to `user-index.jsp` if successful or
   redirected back to `login.jsp` on failure

Retrieve CM11s - via list-orders
================================

The order list URI is: `${BASE_URL}sv/orders/list-orders.do`

1. Login

2. `GET` the list orders page and note the form parameter values for:

   * `slc_KEY`
   * `slc_CONTEXT_KEY`

   (The `slc_KEY` appears to be unique per session and context.)

   You'll need to submit these with any `POST` requests.

3. Either do things quick-and-easy or more robustly, in both cases
   you'll want to submit the following column ordering parameters with
   the request:

   * `applyAtts` equal to `true`
   * `slc_ATT_REORDER_${slc_KEY}` with the values:
     * `OrderNumber`
     * `OrderLine`
     * `ItemID`
     * `ItemRevision`
     * `UM`
     * `OrderQuantity`
     * `DueDate`

   Quick-and-easy:
   `POST` to the order list URI with:
   * the column ordering parameters
   * `slc_CND_CTX_${slc_key}` set to `OrderNumber:java.lang.String`
   * `slc_CND_OP_${slc_KEY}` set to `=`
   * `slc_CND_VAL_${slc_KEY}` set to the customer PO
   * `slc_PAGE_$slc_KEY}` set to `1`
   * `slc_PAGE_SIZE_${slc_KEY}` set to `250`

   More robustly:
   * `POST` to the order list URI with `activeTab=filter`
   * Search for `#fmain input[value^=OrderNumber]`
   * Note the `name` of the input
   * `POST` to the list orders URI with:
     * the column ordering parameters
     * `name_of_input.replace("CTX", "VAL")` set to the customer PO
     * `name_of_input.replace("CTX", "OP")` set to `=`
     * `$(#fmain input[name^=slc_PAGE]).name` set to `1`
     * `$(#fmain input[name^=slc_PAGE_SIZE]).name` set to `250`

   Also note that _if_ there are multiple pages of lines for the
   selected order then there will be one or more `button` elements in
   the `Pagination` class with a numeric value (e.g. `<button
   class="Pagination">2</button>`).

   You would need to repeat the same post request with each increment of
   in this range as the value of `slc_PAGE_${slc_KEY}`.

4. For each `tr.alt` element:

   1. Transform to a map using the values from the `#AttributeNames` row
      `th` columns `javascript` links as the keys (note there'll be
      `td`s in that row for columns that don't contain data.)

      The `javascript` `hrefs` will contain the string `='6:<column
      name>';`.

   2. Parse out the form parameters from the `Item` href, the relevant
      parts are:

      * `action='{url}'` the item page URI
      * `setParam(form, parameter, value)` calls which include:
        * `itemPlaceSysID` the item ID
        * `supplierOrgSysID` our organization ID
        * `poLineSysID` the ID of the line, effectively the "referrer"

   Again, there's a choice of quick-and-easy or slightly more robust.

   Quick-and-easy:
   1. `GET` `${BASE_URL}sv/item/BOMReport.do` with parameters:

      * `itemPlaceSysID` from the item URI
      * `entitySysID` the same as `itemPlaceSysID`
      * `orgSysID` the value of `customerOrgSysID` from the link in the
        `$('#orgContext a')` element on the `list-orders.do` page
      * `level` set to `999`

   More robustly:
   1. `POST` to the URL discovered above with the form parameters
   2. Find the BOM Report link:
      `$('[onclick*=BOMReport]')`
   3. Pull the URL out (it's the first argument to the
     `javascript:openNewWindow` call of the `onclick` attribute
   4. Adjust the `level` parameter of the URL to `999`
   5. `GET` the BOM report page

5. Select the BOM headings:
   `$('.Grid tr.alt')`
   ... and details:
   `$('.Grid tr:not(.alt)')`

6. Treating the retrieved BOM as a sequence of maps:

   1. Remove non-breaking spaces from all the values
   2. Filter out the `Parent` part row
   3. Filter out any entries with a `Quantity Per` of `0.00`
   4. Turn into a nested map (based on changes to the `Level`)
