import { describe, expect, it } from 'vitest';
import { formatJpy } from './format';

describe('formatJpy', () => {
    it('formats an amount as JPY with grouping', () => {
        expect(formatJpy(2000)).toContain('2,000');
    });

    it('formats zero', () => {
        expect(formatJpy(0)).toContain('0');
    });
});
