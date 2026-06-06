package com.stockwidget.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class StockWidgetRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new StockRowsFactory(getApplicationContext());
    }

    private static class StockRowsFactory implements RemoteViewsFactory {
        private final Context context;
        private List<StockItem> stocks = new ArrayList<>();
        private List<StockQuote> quotes = new ArrayList<>();

        StockRowsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            loadRows();
        }

        @Override
        public void onDataSetChanged() {
            loadRows();
        }

        @Override
        public void onDestroy() {
            stocks.clear();
            quotes.clear();
        }

        @Override
        public int getCount() {
            if (!quotes.isEmpty()) {
                return quotes.size();
            }
            return stocks.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= getCount()) {
                return null;
            }
            if (!quotes.isEmpty()) {
                return quoteRow(quotes.get(position));
            }
            return statusRow(stocks.get(position));
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            if (!quotes.isEmpty() && position < quotes.size()) {
                return quotes.get(position).item.code.hashCode();
            }
            if (position < stocks.size()) {
                return stocks.get(position).code.hashCode();
            }
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void loadRows() {
            stocks = StockRepository.loadStocks(context);
            quotes = StockRepository.loadQuoteCache(context, stocks);
        }

        private RemoteViews quoteRow(StockQuote quote) {
            RemoteViews row = baseRow(quote.item.name, quote.item.code);
            if (quote.hasError()) {
                row.setTextViewText(R.id.row_price, "조회 실패");
                row.setTextViewText(R.id.row_change, quote.error);
                row.setTextColor(R.id.row_price, Color.rgb(248, 113, 113));
                row.setTextColor(R.id.row_change, Color.rgb(248, 113, 113));
                return row;
            }

            int color = quote.up ? Color.rgb(248, 113, 113) : Color.rgb(96, 165, 250);
            row.setTextViewText(R.id.row_price, StockQuoteFormatter.formatPrice(quote.price));
            row.setTextViewText(R.id.row_change, StockQuoteFormatter.formatChange(quote));
            row.setTextColor(R.id.row_price, color);
            row.setTextColor(R.id.row_change, color);
            return row;
        }

        private RemoteViews statusRow(StockItem stock) {
            RemoteViews row = baseRow(stock.name, stock.code);
            row.setTextViewText(R.id.row_price, MarketHours.statusLabel());
            row.setTextViewText(R.id.row_change, MarketHours.detailText());
            row.setTextColor(R.id.row_price, Color.rgb(148, 163, 184));
            row.setTextColor(R.id.row_change, Color.rgb(148, 163, 184));
            return row;
        }

        private RemoteViews baseRow(String name, String code) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_stock_row);
            row.setTextViewText(R.id.row_name, name);
            row.setTextViewText(R.id.row_code, code);
            return row;
        }
    }
}
