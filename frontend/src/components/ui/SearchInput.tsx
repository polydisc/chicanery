import classNames from 'classnames';
import React from 'react';
import { forwardRef, InputHTMLAttributes } from 'react';
import { BiSearch } from 'react-icons/bi';

export type SearchInputProps = InputHTMLAttributes<HTMLInputElement>;

const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(({ className, type, ...props }, ref) => (
    <label className='relative'>
        <div className='inline-block absolute p-3'>
            <BiSearch size={18} />
        </div>
        <input
            ref={ref}
            inputMode='text'
            className={classNames('p-3 pl-10 rounded-lg w-full', className)}
            type='text'
            {...props}
        />
    </label>
));
SearchInput.displayName = 'SearchInput';

export default SearchInput;
