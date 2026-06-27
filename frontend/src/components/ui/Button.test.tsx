import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Button from './Button';

describe('Button', () => {
    it('renders its children', () => {
        render(<Button>Add to basket</Button>);
        expect(screen.getByRole('button', { name: 'Add to basket' })).toBeInTheDocument();
    });

    it('calls onClick when clicked', async () => {
        const onClick = vi.fn();
        render(<Button onClick={onClick}>Buy</Button>);
        await userEvent.click(screen.getByRole('button', { name: 'Buy' }));
        expect(onClick).toHaveBeenCalledOnce();
    });

    it('does not fire onClick when disabled', async () => {
        const onClick = vi.fn();
        render(
            <Button onClick={onClick} disabled>
                Pay
            </Button>
        );
        await userEvent.click(screen.getByRole('button', { name: 'Pay' }));
        expect(onClick).not.toHaveBeenCalled();
    });
});
