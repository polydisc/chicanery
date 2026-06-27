const jpyFormatter = new Intl.NumberFormat('ja-JP', {
    style: 'currency',
    currency: 'JPY'
});

export const formatJpy = (amount: number): string => jpyFormatter.format(amount);
